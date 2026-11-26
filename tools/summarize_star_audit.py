#!/usr/bin/env python3
"""Aggregate LargeStarAuditTest counters; retain numerators/denominators, not just percentages.
Usage: python3 tools/summarize_star_audit.py /path/to/run > summary.json
Rates are simulation observations, NOT estimates of real people's true strategies.
"""
import csv, json, math, sys
from pathlib import Path

def rate(k, n):
    if not n: return {'events':k,'opportunities':n,'percent':None,'wilson95':None}
    p=k/n; z=1.96; d=1+z*z/n; c=(p+z*z/(2*n))/d; h=z*math.sqrt(p*(1-p)/n+z*z/(4*n*n))/d
    return {'events':k,'opportunities':n,'percent':round(100*p,3),'wilson95':[round(100*(c-h),3),round(100*(c+h),3)]}

def summarize(path):
    path=Path(path)
    totals=list(csv.DictReader((path/'total-summary.csv').open()))
    rows=list(csv.DictReader((path/'total-streets.csv').open()))
    for r in rows:
        for k in list(r)[11:]: r[k]=float(r[k]) if k.endswith('_sum') else int(r[k])
    result={'run':json.loads((path/'total-progress.json').read_text()),'players':{}}
    for id in sorted(set(r['id'] for r in totals)):
        player={}
        for table in ['8','6']:
            t=next(r for r in totals if r['id']==id and r['table_size']==table)
            n=int(t['hands']); rr=[r for r in rows if r['id']==id and r['table_size']==table]
            entry={'hands':n,'vpip':rate(int(t['vpip']),n),'pfr':rate(int(t['pfr']),n),'preflop_shoves':rate(int(t['preflop_shoves']),n),'streets':{}}
            for street in ['PREFLOP','FLOP','TURN','RIVER']:
                sr=[r for r in rr if r['street']==street]
                def metric(pred,event='raise'):
                    subset=[r for r in sr if pred(r)]
                    return rate(sum(r[event] for r in subset),sum(r['decisions'] for r in subset))
                metrics={'decisions':sum(r['decisions'] for r in sr),'fold_facing_bet':metric(lambda r:r['price']!='free','fold'),'raise_when_legal':metric(lambda r:r['raise_permission']=='can_raise')}
                if street=='PREFLOP':
                    metrics['three_bet']=metric(lambda r:r['line'].startswith('r1') and r['raise_permission']=='can_raise')
                else:
                    metrics.update({'bet_when_checked_to':metric(lambda r:r['price']=='free' and r['raise_permission']=='can_raise'),
                        'initiative_bet':metric(lambda r:r['price']=='free' and r['initiative']=='initiative' and r['raise_permission']=='can_raise'),
                        'hu_air_bet':metric(lambda r:r['price']=='free' and r['field']=='HU' and r['hand_class']=='0' and r['raise_permission']=='can_raise'),
                        'multiway_air_bet':metric(lambda r:r['price']=='free' and r['field']!='HU' and r['hand_class']=='0' and r['raise_permission']=='can_raise'),
                        'draw_raise':metric(lambda r:r['price']!='free' and r['hand_class']=='1' and r['raise_permission']=='can_raise'),
                        'check_raise':metric(lambda r:r['line']=='check_raise' and r['raise_permission']=='can_raise'),
                        'call_facing_bet':metric(lambda r:r['price']!='free','call'),
                        'overbet_share':rate(sum(r['overbets'] for r in sr),sum(sum(r[k] for k in ['small_bets','medium_bets','large_bets','pot_bets','overbets']) for r in sr))})
                entry['streets'][street]=metrics
            player[table]=entry
        result['players'][id]=player
    return result
if __name__=='__main__': json.dump(summarize(sys.argv[1]),sys.stdout,ensure_ascii=False,indent=2);print()
