/**
 * Notification endpoints — admin authoring + app delivery.
 *
 * Admin routes mount under /api/admin (verifyAdmin already applied by the
 * router). User routes mount under /api and require a normal JWT.
 */
const notifications = require('../services/notificationService');

function fail(res, error, fallback) {
  const status = error?.statusCode === 400 ? 400 : 500;
  const message = status === 400 ? error.message : 'Internal Server Error';
  if (status === 500) console.error(`[Notifications] ${fallback}:`, error);
  return res.status(status).json({ success: false, error: message });
}

/** POST /api/admin/notifications — create + send. */
async function adminCreateNotification(req, res) {
  try {
    const { title, body, categories } = req.body || {};
    const created = await notifications.createNotification({
      title,
      body,
      categories,
      createdBy: req.user?.userId,
    });
    return res.status(201).json({ success: true, notification: created });
  } catch (error) {
    return fail(res, error, 'adminCreateNotification');
  }
}

/** GET /api/admin/notifications — history with read counts. */
async function adminListNotifications(req, res) {
  try {
    const list = await notifications.listNotifications({ limit: req.query.limit });
    return res.json({ success: true, notifications: list });
  } catch (error) {
    return fail(res, error, 'adminListNotifications');
  }
}

/** DELETE /api/admin/notifications/:id */
async function adminDeleteNotification(req, res) {
  try {
    const removed = await notifications.deleteNotification(req.params.id);
    if (!removed) {
      return res.status(404).json({ success: false, error: 'নোটিফিকেশন পাওয়া যায়নি' });
    }
    return res.json({ success: true });
  } catch (error) {
    return fail(res, error, 'adminDeleteNotification');
  }
}

/** GET /api/notifications — unread notices for the signed-in account. */
async function listMyNotifications(req, res) {
  try {
    const userId = req.user.userId;
    const cats = await notifications.resolveUserCategories(userId);
    const list = await notifications.listUnreadForUser(userId, cats);
    return res.json({ success: true, notifications: list });
  } catch (error) {
    return fail(res, error, 'listMyNotifications');
  }
}

/** POST /api/notifications/:id/read — idempotent read receipt. */
async function markNotificationRead(req, res) {
  try {
    await notifications.markRead(req.user.userId, req.params.id);
    return res.json({ success: true });
  } catch (error) {
    return fail(res, error, 'markNotificationRead');
  }
}

module.exports = {
  adminCreateNotification,
  adminListNotifications,
  adminDeleteNotification,
  listMyNotifications,
  markNotificationRead,
};
