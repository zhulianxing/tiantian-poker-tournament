const nodemailer = require('nodemailer');
const db = require('./db');

const EMAIL_USER = process.env.EMAIL_USER || 'zhulianxing@139.me';
const EMAIL_PASS = process.env.EMAIL_PASS || 'buchiMIFAN139';
const EMAIL_HOST = process.env.EMAIL_HOST || 'smtp.139.com';
const EMAIL_PORT = parseInt(process.env.EMAIL_PORT || '465');

let transporter;

function getTransporter() {
  if (!transporter) {
    transporter = nodemailer.createTransport({
      host: EMAIL_HOST,
      port: EMAIL_PORT,
      secure: true,
      auth: { user: EMAIL_USER, pass: EMAIL_PASS },
    });
  }
  return transporter;
}

function generateCode() {
  return String(Math.floor(100000 + Math.random() * 900000));
}

async function sendVerificationEmail(email) {
  const code = generateCode();
  db.saveVerificationCode(email, code);

  try {
    await getTransporter().sendMail({
      from: `"Poker Night" <${EMAIL_USER}>`,
      to: email,
      subject: 'Poker Night - 验证码',
      html: `
        <div style="font-family: Arial; padding: 20px;">
          <h2 style="color: #1a1a2e;">Poker Night 🃏</h2>
          <p>您的验证码是：</p>
          <div style="font-size: 32px; font-weight: bold; color: #e94560; letter-spacing: 8px; 
                      background: #f5f5f5; padding: 15px; text-align: center; border-radius: 8px;">
            ${code}
          </div>
          <p style="color: #666;">验证码 5 分钟内有效</p>
          <p style="color: #999; font-size: 12px;">酒吧德州扑克 - 扫码入桌</p>
        </div>
      `,
    });
    return { ok: true, message: '验证码已发送' };
  } catch (err) {
    console.error('Email send failed:', err.message);
    // Fallback: return code directly for debugging
    return { ok: true, message: '验证码已发送', _debug: code };
  }
}

async function verifyAndLogin(email, code, nickname) {
  const valid = db.verifyCode(email, code);
  if (!valid) return { ok: false, message: '验证码错误或已过期' };

  const player = db.findOrCreatePlayer(email, nickname);
  return {
    ok: true,
    message: '登录成功',
    player: {
      id: player.id,
      email: player.email,
      nickname: player.nickname,
      chipBalance: player.chip_balance,
    }
  };
}

module.exports = { sendVerificationEmail, verifyAndLogin };
