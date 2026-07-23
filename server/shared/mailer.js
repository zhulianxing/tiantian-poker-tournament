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

// 品牌 HTML 邮件模板（与网站同一视觉：深黑底 #07070c、金色 #FFD700）
function buildHtml(code, purpose) {
  const action = purpose === 'register' ? '注册' : '登录';
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head><meta charset="UTF-8"><meta name="viewport" content="width=device-width, initial-scale=1.0"></head>
<body style="margin:0;padding:0;background:#07070c;">
<table width="100%" cellpadding="0" cellspacing="0" style="background:#07070c;padding:40px 16px;">
  <tr><td align="center">
    <table width="480" cellpadding="0" cellspacing="0" style="max-width:480px;width:100%;background:#101018;border:1px solid rgba(201,162,39,.35);border-radius:16px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI','PingFang SC','Microsoft YaHei',sans-serif;">
      <tr><td align="center" style="padding:36px 32px 0;">
        <div style="color:#FFD700;font-size:22px;font-weight:800;letter-spacing:2px;">♠ 天天扑克锦标赛</div>
        <div style="color:#9c9ca8;font-size:10px;letter-spacing:4px;padding-top:6px;">TIANTIAN POKER TOURNAMENT</div>
      </td></tr>
      <tr><td style="padding:28px 32px 0;color:#e8e8ec;font-size:14px;line-height:1.8;">
        你正在进行【${action}】操作，验证码如下：
      </td></tr>
      <tr><td align="center" style="padding:24px 32px 8px;">
        <div style="display:inline-block;background:rgba(255,215,0,.08);border:1px solid rgba(201,162,39,.4);border-radius:12px;padding:16px 40px;color:#FFD700;font-size:34px;font-weight:800;letter-spacing:10px;font-family:'Courier New',monospace;">${code}</div>
      </td></tr>
      <tr><td align="center" style="padding:0 32px;color:#9c9ca8;font-size:12px;">
        验证码 10 分钟内有效，请勿泄露给他人
      </td></tr>
      <tr><td style="padding:28px 32px 32px;">
        <div style="border-top:1px solid rgba(255,255,255,.08);padding-top:20px;color:#55555f;font-size:11px;line-height:1.9;">
          如果不是你本人操作，请忽略此邮件。<br>
          © 2026 天天扑克锦标赛 · <a href="https://poker.clawclaw.tech" style="color:#c9a227;text-decoration:none;">poker.clawclaw.tech</a>
        </div>
      </td></tr>
    </table>
  </td></tr>
</table>
</body>
</html>`;
}

/**
 * 发送验证码邮件（HTML 品牌排版 + 纯文本兜底）
 * 开发模式（NODE_ENV=development 或未配置 SMTP_PASS）下仅输出到控制台
 */
async function sendCode(email, code, purpose) {
  const isDev = process.env.NODE_ENV === 'development' || !process.env.SMTP_PASS;
  const subject = purpose === 'register' ? '天天扑克锦标赛 · 注册验证码' : '天天扑克锦标赛 · 登录验证码';
  const text = `您的验证码是: ${code}\n\n验证码10分钟内有效。\n\n如果不是您本人操作，请忽略此邮件。\n\n— 天天扑克锦标赛`;
  const html = buildHtml(code, purpose);

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
    html,
  });
}

module.exports = { sendCode, buildHtml };
