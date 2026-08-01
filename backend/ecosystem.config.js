module.exports = {
  apps: [
    {
      name: 'payment-checker-api',
      script: 'app.js',
      cwd: '/var/www/payment-checker/current',
      instances: 1,
      exec_mode: 'fork',
      watch: false,
      autorestart: true,
      max_restarts: 10,
      restart_delay: 5000,
      time: true,
      env: {
        NODE_ENV: 'production',
        PORT: 3000
      }
    }
  ]
};
