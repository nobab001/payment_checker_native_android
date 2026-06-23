const prisma = require('./prisma');

async function query(sql, params = []) {
  const isSelect = /^\s*(SELECT|SHOW|DESCRIBE)/i.test(sql);
  if (isSelect) {
    const results = await prisma.$queryRawUnsafe(sql, ...params);
    return JSON.parse(JSON.stringify(results, (key, value) => {
      if (typeof value === 'bigint') return Number(value);
      if (typeof value === 'boolean') return value ? 1 : 0;
      return value;
    }));
  } else {
    return await prisma.$transaction(async (tx) => {
      const affectedRows = await tx.$executeRawUnsafe(sql, ...params);
      let insertId = 0;
      if (/^\s*INSERT/i.test(sql)) {
         const idRes = await tx.$queryRawUnsafe('SELECT LAST_INSERT_ID() as insertId');
         if (idRes && idRes.length > 0) {
           insertId = Number(idRes[0].insertId);
         }
      }
      return { affectedRows, insertId };
    });
  }
}

module.exports = {
  query,
  pool: { execute: query }, // Legacy mock just in case
  ensureDatabaseExists: async () => {}, // Handled by Prisma
  ensureGatewayMethodsTable: async () => { return true; }
};
