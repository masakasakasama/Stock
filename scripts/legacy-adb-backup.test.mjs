import test from 'node:test';
import assert from 'node:assert/strict';
import {parsePreferences,encrypt,decrypt} from './legacy-adb-backup.mjs';
test('legacy XML conversion preserves exact integer types, empty text, escaping and Unicode sets',()=>{
  const prefs=parsePreferences(Buffer.from('<map><string name="watchlist">AAPL,7203.T &amp; 日本語</string><string name="empty"/><long name="time" value="9223372036854775807"/><int name="count" value="42"/><boolean name="enabled" value="true"/><float name="ratio" value="1.25"/><set name="seen"><string>b</string><string>a</string></set></map>'));
  assert.deepEqual(prefs.time,{t:'l',v:'9223372036854775807'});
  assert.equal(prefs.watchlist.v,'AAPL,7203.T & 日本語');assert.equal(prefs.empty.v,'');
  assert.deepEqual(prefs.seen.v,['a','b']);assert.equal(prefs.ratio.t,'f');
  const raw=JSON.stringify(prefs),encrypted=encrypt(raw,'fixture-strong-password');
  assert.equal(decrypt(encrypted,'fixture-strong-password'),raw);
  assert.throws(()=>decrypt(encrypted,'fixture-wrong-password'));
  encrypted[encrypted.length-1]^=1;assert.throws(()=>decrypt(encrypted,'fixture-strong-password'));
});
test('malformed XML, entities and unknown types cannot silently produce incomplete backups',()=>{
  for(const raw of ['<map>','<!DOCTYPE map [<!ENTITY x "bad">]><map/>','<map><double name="v" value="1"/></map>','<map><int name="v" value="1"/><int name="v" value="2"/></map>'])assert.throws(()=>parsePreferences(Buffer.from(raw)));
});
