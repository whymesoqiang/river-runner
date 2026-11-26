#!/usr/bin/env python3
"""Validate completed training-policy audits, not resemblance to unknown human ground truth."""
import json, sys
from summarize_star_audit import summarize

def check(final, holdout):
    for report in (final,holdout):
        assert report['run']['hands']>=1_000_000
        for id,p in report['players'].items():
            if id=='hero': continue
            t=p['8'];assert t['hands']>500_000,(id,t['hands'])
            for street in ['FLOP','TURN','RIVER']:
                assert t['streets'][street]['decisions']>500,(id,street)
            assert t['vpip']['events']>=t['pfr']['events']
        p=report['players']
        metric=lambda id,street,m:p[id]['8']['streets'][street][m]['percent']
        for street in ['TURN','RIVER']:
            assert metric('tan_xuan',street,'hu_air_bet')>metric('peter',street,'hu_air_bet')+15
            assert metric('keating',street,'call_facing_bet')>metric('wesley',street,'call_facing_bet')+5
        for id in ['dwan','tan_xuan','wesley']:
            assert metric(id,'TURN','overbet_share')>2
        assert p['texas_mike']['8']['pfr']['percent']>p['keating']['8']['pfr']['percent']+5
        assert p['britney']['8']['preflop_shoves']['percent']>10
        assert p['st_wang']['8']['vpip']['percent']<p['texas_mike']['8']['vpip']['percent']-20
    for id,p in final['players'].items():
        for table in ['8','6']:
            for metric in ['vpip','pfr']:
                a=p[table][metric]['percent'];b=holdout['players'][id][table][metric]['percent']
                assert abs(a-b)<1,(id,table,metric,a,b)
    return {'passed':True,'description':'Completeness, stage coverage, persona ordering and independent-seed preflop stability; no assertion of exact real-player fidelity.'}
if __name__=='__main__': print(json.dumps(check(summarize(sys.argv[1]),summarize(sys.argv[2]))))
