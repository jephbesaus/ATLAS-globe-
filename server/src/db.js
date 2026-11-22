const Database = require('better-sqlite3'), path = require('path');
const { DATA } = require('./config');
const db = new Database(path.join(DATA, 'atlas.db'));
db.pragma('journal_mode = WAL'); db.pragma('foreign_keys = ON');
db.exec(`
CREATE TABLE IF NOT EXISTS users(
  id TEXT PRIMARY KEY, pub TEXT NOT NULL, nom TEXT DEFAULT '', prenom TEXT DEFAULT '', phone TEXT DEFAULT '', email TEXT DEFAULT '',
  email_verified INTEGER DEFAULT 0, continent TEXT DEFAULT '', country TEXT DEFAULT '', province TEXT DEFAULT '', city TEXT DEFAULT '',
  bio TEXT DEFAULT '', created_at INTEGER, last_seen INTEGER, stub INTEGER DEFAULT 0,
  blocked INTEGER DEFAULT 0, block_reason TEXT DEFAULT '', reseller INTEGER DEFAULT 0, cert INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS seen_packets(id TEXT PRIMARY KEY, t INTEGER);
CREATE TABLE IF NOT EXISTS outbox(id INTEGER PRIMARY KEY AUTOINCREMENT, user TEXT, packet TEXT, created_at INTEGER);
CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY, user TEXT, dir TEXT, text TEXT, created_at INTEGER);
CREATE TABLE IF NOT EXISTS orders(id TEXT PRIMARY KEY, user TEXT, plan_id TEXT, plan_label TEXT, amount INTEGER, reseller_name TEXT,
  status TEXT DEFAULT 'awaiting', created_at INTEGER, decided_at INTEGER, tid TEXT);
CREATE TABLE IF NOT EXISTS posts(id TEXT PRIMARY KEY, author TEXT, author_name TEXT, kind TEXT, text TEXT, media TEXT, created_at INTEGER, deleted INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS groups(id TEXT PRIMARY KEY, name TEXT, creator TEXT, members TEXT, created_at INTEGER, updated_at INTEGER);
CREATE TABLE IF NOT EXISTS cert_requests(id INTEGER PRIMARY KEY AUTOINCREMENT, user TEXT, level INTEGER, front TEXT, back TEXT, selfie TEXT,
  status TEXT DEFAULT 'pending', reason TEXT DEFAULT '', created_at INTEGER, decided_at INTEGER);
CREATE TABLE IF NOT EXISTS usage_reports(id INTEGER PRIMARY KEY AUTOINCREMENT, reseller TEXT, tid TEXT, bytes INTEGER, used_ms INTEGER, created_at INTEGER);
CREATE TABLE IF NOT EXISTS ratings(order_id TEXT PRIMARY KEY, reseller TEXT, stars INTEGER, created_at INTEGER);
CREATE TABLE IF NOT EXISTS announcements(id INTEGER PRIMARY KEY AUTOINCREMENT, title TEXT, body TEXT, whatsapp TEXT, telegram TEXT, support TEXT, audience INTEGER, created_at INTEGER);
CREATE TABLE IF NOT EXISTS email_codes(user TEXT PRIMARY KEY, email TEXT, phone TEXT, code_hash TEXT, expires INTEGER, attempts INTEGER DEFAULT 0);
CREATE TABLE IF NOT EXISTS admin(k TEXT PRIMARY KEY, v TEXT);
CREATE TABLE IF NOT EXISTS likes(post TEXT, user TEXT, PRIMARY KEY(post,user));
CREATE TABLE IF NOT EXISTS ac_clients(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, country TEXT, email TEXT, phone TEXT, token TEXT UNIQUE, device TEXT,
  balance INTEGER DEFAULT 0, expires_at INTEGER DEFAULT 0, config_id INTEGER, status TEXT DEFAULT 'active', created_at INTEGER);
CREATE TABLE IF NOT EXISTS ac_configs(id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT, source TEXT, items TEXT, expire_at INTEGER DEFAULT 0, created_at INTEGER);
CREATE TABLE IF NOT EXISTS ac_tariffs(id INTEGER PRIMARY KEY AUTOINCREMENT, label TEXT, price INTEGER, days INTEGER, active INTEGER DEFAULT 1);
CREATE TABLE IF NOT EXISTS ac_tx(id INTEGER PRIMARY KEY AUTOINCREMENT, client INTEGER, kind TEXT, amount INTEGER, note TEXT, created_at INTEGER);
`);
module.exports = db;
