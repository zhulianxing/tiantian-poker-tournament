// shared/mailer.js — 邮件发送服务
'use strict';

let nodemailer;
try {
  nodemailer = require('nodemailer');
} catch (e) {
  console.warn('[Mailer] nodemailer not installed, falling back to console-only mode');
}

// SMTP transporter（懒加载，避免未配置时报错）
let transporter = null;
function getTransporter() {
  if (!nodemailer) return null;
  if (transporter) return transporter;

  const user = process.env.SMTP_USER || 'sms@139.me';
  const pass = process.env.SMTP_PASS || '';

  if (!pass) {
    console.warn('[Mailer] SMTP_PASS not set, emails will be logged to console only');
    return null;
  }

  transporter = nodemailer.createTransport({
    host: process.env.SMTP_HOST || 'smtp.exmail.qq.com',
    port: Number(process.env.SMTP_PORT || 465),
    secure: process.env.SMTP_SECURE !== 'false',
    auth: { user, pass },
  });

  return transporter;
}

/**
 * 发送验证码邮件
 * 开发模式（NODE_ENV=development 或未配置 SMTP_PASS）下仅输出到控制台
 */
async function sendCode(email, code, purpose) {
  const isDev = process.env.NODE_ENV === 'development' || !process.env.SMTP_PASS;
  const subject = purpose === 'register' ? '🎮 天天扑克锦标赛 注册验证码' : '🔑 天天扑克锦标赛 登录验证码';
  const text = `您的验证码是: ${code}\n\n验证码10分钟内有效。\n\n如果不是您本人操作，请忽略此邮件。\n\n— 天天扑克锦标赛 Team`;

  if (isDev) {
    console.log(`\n[DEV] ═══════════════════════════════════════`);
    console.log(`[DEV]  收件人: ${email}`);
    console.log(`[DEV]  主题:   ${subject}`);
    console.log(`[DEV]  验证码: ${code}`);
    console.log(`[DEV]  用途:   ${purpose}`);
    console.log(`[DEV] ═══════════════════════════════════════\n`);
    return;
  }

  const t = getTransporter();
  if (!t) {
    console.warn(`[Mailer] No transporter available, logging to console: ${email} → ${code} (${purpose})`);
    return;
  }

  await t.sendMail({
    from: process.env.SMTP_FROM || `"天天扑克锦标赛" <${process.env.SMTP_USER || 'sms@139.me'}>`,
    to: email,
    subject,
    text,
  });
}

module.exports = { sendCode };
