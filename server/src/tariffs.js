// Même catalogue que l'application (prix en FC). Modifiable ici ; l'application suit les identifiants.
const T = (id, label, fc, min) => ({ id, kind: 'time', label, fc, min, mb: 0 });
const D = (id, label, fc, mb) => ({ id, kind: 'data', label, fc, min: 0, mb });
const PLANS = [T('t30', '30 minutes', 200, 30), T('t120', '2 heures', 500, 120), T('t480', '8 heures', 1000, 480), T('t840', '14 heures', 1500, 840),
  T('t1440', '24 heures', 3000, 1440), T('t2880', '48 heures', 4000, 2880), T('t7200', '5 jours', 10000, 7200), T('t10080', '7 jours', 11000, 10080),
  T('t28800', '20 jours', 20000, 28800), T('t43200', '30 jours', 25000, 43200),
  D('d500', '500 Mo', 500, 500), D('d1000', '1 Go', 1000, 1000), D('d2000', '2 Go', 1500, 2000), D('d3500', '3,5 Go', 2000, 3500), D('d4000', '4 Go', 2500, 4000),
  D('d8000', '8 Go', 5000, 8000), D('d10000', '10 Go', 8000, 10000), D('d15000', '15 Go', 12000, 15000), D('d20000', '20 Go', 18000, 20000),
  D('d30000', '30 Go', 20000, 30000), D('d50000', '50 Go', 30000, 50000)];
const find = id => PLANS.find(p => p.id === id);
const durMs = p => p.min > 0 ? p.min * 60000 : 30 * 24 * 3600 * 1000;
module.exports = { PLANS, find, durMs };
