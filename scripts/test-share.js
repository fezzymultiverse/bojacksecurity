#!/usr/bin/env node
// Round-trip test of the share format: sender code (www/index.html) -> receiver code (www/share-template.html).
const fs = require('fs'), vm = require('vm');
const pick = (file, a, b) => { const s = fs.readFileSync(file, 'utf8'); const i = s.indexOf(a), j = s.indexOf(b); if (i < 0 || j < 0) throw new Error(`markers ${a} not found in ${file}`); return s.slice(i + a.length, j); };
const sender = pick('www/index.html', '/*SHARE-CORE-BEGIN*/', '/*SHARE-CORE-END*/');
const receiver = pick('www/share-template.html', '/*BJ-DECRYPT-BEGIN*/', '/*BJ-DECRYPT-END*/');
const tpl = fs.readFileSync('www/share-template.html', 'utf8');
const ctx = { crypto: globalThis.crypto, TextEncoder, TextDecoder, Blob, btoa, atob, fetch, Uint8Array, DataView, console,
  enc: new TextEncoder(),
  FileReader: class { readAsDataURL(b) { b.arrayBuffer().then(ab => { this.result = 'data:application/octet-stream;base64,' + Buffer.from(ab).toString('base64'); this.onload(); }, e => { this.error = e; this.onerror(); }); } } };
vm.createContext(ctx);
vm.runInContext(sender + '\n' + receiver + '\nthis.shareEncrypt=shareEncrypt;this.shareHtml=shareHtml;this.bjDecrypt=bjDecrypt;this.sha256hex=sha256hex;', ctx);
let failed = 0;
const t = async (name, fn) => { try { await fn(); console.log('ok:   ' + name); } catch (e) { failed++; console.error('FAIL: ' + name + ' -> ' + e.message); } };
const eq = (a, b, m) => { if (a !== b) throw new Error(m || `${a} !== ${b}`); };
const rejects = async (p, name) => { try { await p; } catch (e) { if (!name || e.name === name || /valid|Unsupported|Corrupt/.test(e.message)) return; throw new Error('wrong error: ' + e.name + ' ' + e.message); } throw new Error('expected rejection'); };
const parts = html => ({ H: JSON.parse(html.match(/<script id="bjh" type="application\/json">([\s\S]*?)<\/script>/)[1]), C: html.match(/<script id="bjc" type="application\/octet-stream">([\s\S]*?)<\/script>/)[1].trim() });

(async () => {
  const file = new Uint8Array(1.5e6); for (let o = 0; o < file.length; o += 65536) crypto.getRandomValues(file.subarray(o, o + 65536));
  const meta = { name: 'Passport scan ünï.pdf', mime: 'application/pdf', size: file.length, mod: 1700000000000, sha256: await ctx.sha256hex(file), shared: Date.now(), note: 'Open in Chrome' };
  const pw = 'Xk3mP-9aRt2-hQw7L-zN4vB';
  const { hdr, ctB64 } = await ctx.shareEncrypt(file, meta, pw);
  const html = ctx.shareHtml(tpl, hdr, ctB64);
  const { H, C } = parts(html);

  await t('correct password restores the exact file and metadata', async () => {
    const r = await ctx.bjDecrypt(pw, H, C);
    eq(Buffer.compare(Buffer.from(r.file), Buffer.from(file)), 0, 'file bytes differ');
    eq(r.meta.name, meta.name); eq(r.meta.sha256, meta.sha256); eq(r.meta.note, 'Open in Chrome');
  });
  await t('wrong password is rejected', () => rejects(ctx.bjDecrypt(pw + 'x', H, C), 'OperationError'));
  await t('wrong case is rejected', () => rejects(ctx.bjDecrypt(pw.toLowerCase(), H, C), 'OperationError'));
  await t('empty password is rejected', () => rejects(ctx.bjDecrypt('', H, C), 'OperationError'));
  await t('flipped ciphertext bit is rejected (tamper detection)', async () => {
    const b = Buffer.from(C, 'base64'); b[1000] ^= 1; await rejects(ctx.bjDecrypt(pw, H, b.toString('base64')), 'OperationError');
  });
  await t('altered salt/iv header is rejected', async () => {
    const s = Buffer.from(H.salt, 'base64'); s[0] ^= 1; await rejects(ctx.bjDecrypt(pw, { ...H, salt: s.toString('base64') }, C), 'OperationError');
    const v = Buffer.from(H.iv, 'base64'); v[0] ^= 1; await rejects(ctx.bjDecrypt(pw, { ...H, iv: v.toString('base64') }, C), 'OperationError');
  });
  await t('tampered iteration count is rejected', () => rejects(ctx.bjDecrypt(pw, { ...H, iter: H.iter + 1 }, C), 'OperationError'));
  await t('absurd iteration count is refused before any work', () => rejects(ctx.bjDecrypt(pw, { ...H, iter: 4e9 }, C)));
  await t('unicode password works in NFC and NFD forms', async () => {
    const p1 = 'pässwörd-é-ñ-12345'; const e = await ctx.shareEncrypt(new Uint8Array([1, 2, 3]), { name: 'a' }, p1.normalize('NFC'));
    const h = parts(ctx.shareHtml(tpl, e.hdr, e.ctB64));
    const r = await ctx.bjDecrypt(p1.normalize('NFD'), h.H, h.C); eq(r.file.length, 3);
  });
  await t('two shares of the same file+password differ (fresh salt and IV)', async () => {
    const e = await ctx.shareEncrypt(file.subarray(0, 100), meta, pw); if (e.ctB64 === ctB64.slice(0, e.ctB64.length) || e.hdr.salt === hdr.salt) throw new Error('salt/ciphertext reused');
  });
  await t('file name is not visible anywhere in the shared page', async () => {
    if (html.includes('Passport') || html.includes('application/pdf')) throw new Error('metadata leaked in plaintext');
  });
  await t('template placeholders were replaced, nothing left over', async () => { if (html.includes('__BJ_')) throw new Error('placeholder remains'); });
  await t('empty file round-trips', async () => {
    const e = await ctx.shareEncrypt(new Uint8Array(0), { name: 'empty.txt' }, pw); const h = parts(ctx.shareHtml(tpl, e.hdr, e.ctB64));
    eq((await ctx.bjDecrypt(pw, h.H, h.C)).file.length, 0);
  });
  console.log(failed ? `\n${failed} test(s) FAILED` : '\nAll share-format tests passed');
  process.exit(failed ? 1 : 0);
})();
