#!/usr/bin/env node
// CI + local check: every inline script parses, version numbers agree, template placeholders intact.
const fs = require('fs');
let failed = 0;
const fail = m => { console.error('FAIL: ' + m); failed++; };
const ok = m => console.log('ok:   ' + m);

for (const f of ['www/index.html', 'www/share-template.html']) {
  const html = fs.readFileSync(f, 'utf8');
  const re = /<script(?:\s[^>]*)?>([\s\S]*?)<\/script>/g;
  let m, n = 0;
  while ((m = re.exec(html))) {
    const tag = html.slice(m.index, html.indexOf('>', m.index) + 1);
    if (/type="(application\/json|application\/octet-stream)"/.test(tag)) continue;
    n++;
    try { new Function(m[1]); } catch (e) { fail(`${f} script #${n} does not parse: ${e.message}`); }
  }
  if (n) ok(`${f}: ${n} inline script(s) parse`);
  else fail(`${f}: no inline script found`);
}

const tpl = fs.readFileSync('www/share-template.html', 'utf8');
for (const ph of ['__BJ_HDR__', '__BJ_CT__']) {
  if (tpl.split(ph).length !== 2) fail(`share-template.html must contain ${ph} exactly once`);
}
if (!/Content-Security-Policy/.test(tpl)) fail('share-template.html lost its Content-Security-Policy');
if (/https?:\/\//.test(tpl.replace(/<!--[\s\S]*?-->/g, '').replace(/xmlns="[^"]*"/g, ''))) fail('share-template.html must not reference any remote URL');
else ok('share-template.html is self-contained (no remote URLs)');

const ver = (fs.readFileSync('www/index.html', 'utf8').match(/const VER='([^']+)'/) || [])[1];
const log = (fs.readFileSync('CHANGELOG.md', 'utf8').match(/^## (\S+)/m) || [])[1];
if (!ver) fail('VER not found in www/index.html');
else if (ver !== log) fail(`VER (${ver}) does not match top CHANGELOG entry (${log})`);
else ok(`version ${ver} matches CHANGELOG`);
if (fs.existsSync('package.json')) {
  const pv = JSON.parse(fs.readFileSync('package.json', 'utf8')).version;
  if (pv !== ver) fail(`package.json version (${pv}) does not match VER (${ver}); run: npm version ${ver} --no-git-tag-version`);
  else ok('package.json version matches');
}
process.exit(failed ? 1 : 0);
