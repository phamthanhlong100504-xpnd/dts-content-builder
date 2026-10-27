#!/usr/bin/env python3
"""DTS simulation data-only workflow (stdlib). Never executes git or DB operations.

Commands: install, validate-json, audit-assets, upload, verify-media.
Dry-run by default for uploads. Local HTTP only. Source JSON SHA-256 is frozen.
"""
from __future__ import annotations
import argparse
from collections import Counter
from decimal import Decimal
import hashlib
import http.client
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
import time
from urllib.error import HTTPError, URLError
from urllib.parse import urlsplit
from urllib.request import build_opener, ProxyHandler, Request

EXPECTED_HASH = {
    'simulation-scenarios.json': '7cca08cf4baa05be24bd504ae02bf46712e0c27cf99a9aa366c6b5a387439a86',
    'simulation-exam-sets.json': 'b266200a3186fb269947d3b25c8161814dae657bcef02d55d05dbf94894b7a2c',
}
CHAPTER_RANGES = {1:(1,29),2:(30,43),3:(44,63),4:(64,73),5:(74,90),6:(91,120)}
MATRIX = [2,1,2,1,2,2]
BANK_SUBDIR = Path('data/simulations/dts-simulation-120-video-v1')
HTTP = build_opener(ProxyHandler({}))


def require(ok, msg):
    if not ok: raise ValueError(msg)


def file_sha256(p):
    h=hashlib.sha256()
    with Path(p).open('rb') as f:
        while True:
            b=f.read(4*1024*1024)
            if not b:break
            h.update(b)
    return h.hexdigest()


def dump_atomic(path, obj):
    p=Path(path);p.parent.mkdir(parents=True,exist_ok=True)
    tmp=p.with_name(p.name+'.tmp')
    tmp.write_text(json.dumps(obj,indent=2,ensure_ascii=False)+'\n',encoding='utf-8')
    os.replace(tmp,p)


def source_paths(repo):
    root=Path(repo).resolve()/BANK_SUBDIR
    return root,root/'simulation-scenarios.json',root/'simulation-exam-sets.json'


def read_sources(repo):
    root,sp,ep=source_paths(repo)
    for p in (sp,ep):
        require(p.is_file(),f'Thiếu JSON: {p}')
        got=file_sha256(p)
        require(got==EXPECTED_HASH[p.name],f'{p.name}: SHA-256 khác bộ dữ liệu V1 đã chốt. Dừng để rà soát! Got={got}')
    scenarios=json.loads(sp.read_text(encoding='utf-8-sig'))
    sets=json.loads(ep.read_text(encoding='utf-8-sig'))
    validate_structure(scenarios,sets)
    return scenarios,sets


def validate_structure(items,sets):
    require(isinstance(items,list) and len(items)==120,'scenarios phải có 120 bản ghi')
    byid={}
    paths=[]
    for x in items:
        require(isinstance(x,dict),'Invalid scenario object')
        i=x.get('id');n=x.get('scenario_number');ch=x.get('chapter_id')
        require(type(i)==int and 1<=i<=120 and i==n,f'ID/number sai: {i}:{n}')
        require(i not in byid,f'Scenario trùng {i}')
        require(ch in CHAPTER_RANGES and CHAPTER_RANGES[ch][0]<=i<=CHAPTER_RANGES[ch][1],f'Chương sai: {i}:{ch}')
        require(isinstance(x.get('title'),str) and x['title'].strip(),f'Thiếu tên {i}')
        require(isinstance(x.get('chapter_name'),str) and x['chapter_name'].strip(),f'Thiếu chapter_name {i}')
        require(isinstance(x.get('tip'),str) and x['tip'].strip(),f'Thiếu tip {i}')
        require(isinstance(x.get('hazard_sign'),str) and x['hazard_sign'].strip(),f'Thiếu hazard_sign {i}')
        for kind,suffix in (('video_path','.mp4'),('thumbnail_path','_thumb.webp')):
            p=x.get(kind);need=f'simulations/ch{ch}/{i:03d}{suffix}'
            require(p==need,f'{i}: {kind} khác chuẩn: {p} (expected {need})')
            paths.append(p)
        duration=Decimal(str(x.get('duration_seconds')))
        require(duration>0,f'{i}: duration không hợp lệ')
        t=x.get('scoring_timeline')
        require(isinstance(t,dict) and set(t)==set(['t5_start','t4_start','t3_start','t2_start','t1_start','t0_end']),f'{i}: timeline keys sai')
        vals=[Decimal(str(t[k])) for k in ['t5_start','t4_start','t3_start','t2_start','t1_start','t0_end']]
        require(Decimal(0)<vals[0]<vals[1]<vals[2]<vals[3]<vals[4]<vals[5]<=duration,f'{i}: mốc chấm sai số học')
        byid[i]=x
    require(set(byid)==set(range(1,121)),'Thiếu hoặc trùng scenario 1..120')
    require(len(paths)==240 and len(set(paths))==240,'Path video/ảnh trùng')
    require(isinstance(sets,list) and len(sets)==15,'Phải có 15 bộ đề')
    codes=set();covered=set()
    for s in sets:
        sid=s.get('set_id');ids=s.get('scenario_ids')
        require(type(sid)==int and 1<=sid<=15 and sid not in codes,f'Mã đề sai/trùng: {sid}')
        codes.add(sid)
        require(s.get('total_questions')==10 and s.get('max_score')==50,f'Đề {sid} không phải 10 câu / 50 điểm')
        require(s.get('passing_score')==35,f'Đề {sid}: passing_score thay đổi so với bản nguồn')
        require(isinstance(ids,list) and len(ids)==10 and len(set(ids))==10 and all(type(j)==int and j in byid for j in ids),f'Đề {sid}: IDs sai/trùng')
        matrix=[sum(byid[j]['chapter_id']==ch for j in ids) for ch in range(1,7)]
        require(matrix==MATRIX,f'Đề {sid}: ma trận sai {matrix}')
        covered.update(ids)
    require(codes==set(range(1,16)) and covered==set(range(1,121)),'Thiếu mã đề / chưa phủ 120 tình huống')
    print('JSON_STRUCTURE=PASS; SCENARIOS=120; CHAPTERS=6; SETS=15; COVERAGE=120/120; MATRIX=2-1-2-1-2-2')
    print('JSON_SHA256=PASS; V1_FROZEN=true')
    print('NOTE: passing_score=35 is in source JSON; do not expose pass/fail until business approval.')
    print('NOTE: scoring timelines checked mathematically, not yet against actual videos.')


def cmd_install(a):
    root,sp,ep=source_paths(a.repo)
    repo=Path(a.repo).resolve()
    require((repo/'.git').exists(),f'Không thấy thư mục .git: {repo}')
    pairs=[(Path(a.scenarios),sp),(Path(a.sets),ep)]
    for src,dst in pairs:
        require(src.is_file(),f'Nguồn JSON không tồn tại: {src}')
        require(file_sha256(src)==EXPECTED_HASH[dst.name],f'SHA-256 JSON nguồn sai: {src}')
        if dst.exists(): require(file_sha256(dst)==EXPECTED_HASH[dst.name],f'Đích đã tồn tại nhưng nội dung khác, không ghi đè: {dst}')
    scenarios=json.loads(pairs[0][0].read_text(encoding='utf-8-sig'))
    sets=json.loads(pairs[1][0].read_text(encoding='utf-8-sig'))
    validate_structure(scenarios,sets)
    root.mkdir(parents=True,exist_ok=True)
    for src,dst in pairs:
        if not dst.exists():
            shutil.copyfile(src,dst)
            print('COPIED='+str(dst))
        else: print('ALREADY_IDENTICAL='+str(dst))
    print('INSTALL=PASS; source JSON bytes preserved; existing unrelated files untouched')


def locate_assets(assets_root, scenarios):
    base=Path(assets_root).resolve()
    require(base.is_dir(),f'Assets folder không tồn tại: {base}')
    names={}
    for p in base.rglob('*'):
        if p.is_file() and p.suffix.lower() in ('.mp4','.webp'):
            names.setdefault(p.name.lower(),[]).append(p)
    rows=[]
    for s in sorted(scenarios,key=lambda x:x['id']):
        for kind,key,mime in [('video','video_path','video/mp4'),('thumbnail','thumbnail_path','image/webp')]:
            logical=s[key]
            exact=base/Path(logical)
            if exact.is_file():path=exact
            else:
                options=names.get(Path(logical).name.lower(),[])
                require(len(options)==1,f'{logical}: tìm thấy {len(options)} file theo tên; cần đúng 1 file hoặc đặt tại {exact}')
                path=options[0]
            require(path.stat().st_size>0,f'File rỗng: {path}')
            if kind=='thumbnail':
                with path.open('rb') as f:head=f.read(12)
                require(head[:4]==b'RIFF' and head[8:12]==b'WEBP',f'Không phải WebP thật: {path}')
            rows.append({'scenarioId':s['id'],'type':kind,'logicalPath':logical,'localPath':str(path),'fileName':Path(logical).name,
                         'mimeType':mime,'sizeBytes':path.stat().st_size,'sha256':file_sha256(path),'declaredDurationSeconds':s['duration_seconds'] if kind=='video' else None})
    require(len(rows)==240,'Inventory phải có 240 tài nguyên')
    return rows


def probe_video(path):
    cmd=['ffprobe','-v','error','-show_entries','format=format_name,duration:stream=codec_type,codec_name', '-of','json',str(path)]
    p=subprocess.run(cmd,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,check=False)
    require(p.returncode==0,f'ffprobe thất bại cho {path}: {p.stderr[:200]}')
    j=json.loads(p.stdout)
    # An .mp4 extension does not guarantee an MP4 container. MPEG-TS named
    # *.mp4 can be uploaded successfully yet fail to play in browsers.
    container=j.get('format',{}).get('format_name','')
    require('mp4' in container.split(','),
            f'{path}: container thực tế là {container!r}, không phải MP4. Remux bằng ffmpeg -c copy -movflags +faststart trước khi audit/upload')
    streams=j.get('streams') or []
    vc=[x.get('codec_name') for x in streams if x.get('codec_type')=='video']
    require(vc and vc[0]=='h264',f'{path}: cần video H.264 cho độ tương thích trình duyệt; found={vc}')
    return float(j['format']['duration']),vc[0]


def cmd_audit(a):
    items,_=read_sources(a.repo)
    rows=locate_assets(a.assets_root,items)
    ffprobe=shutil.which('ffprobe')
    if not ffprobe:print('WARNING: ffprobe not installed; cannot verify video codec and actual duration.')
    if a.require_ffprobe:require(ffprobe is not None,'--require-ffprobe yêu cầu ffprobe được cài trên máy')
    checked=0
    if ffprobe:
        for row in rows:
            if row['type']!='video':continue
            dur,codec=probe_video(row['localPath'])
            row['probedDurationSeconds']=dur
            row['videoCodec']=codec
            delta=abs(dur-float(row['declaredDurationSeconds']))
            require(delta<=a.duration_tolerance,f"Scenario {row['scenarioId']:03}: video duration={dur:.3f}s so với JSON={row['declaredDurationSeconds']}s; lệch {delta:.3f}s")
            checked+=1
    output={'format':'dts-simulation-asset-inventory-v1','jsonHashes':EXPECTED_HASH,'sourceRoot':str(Path(a.assets_root).resolve()),'ffprobeCheckedVideos':checked,'assets':rows}
    dump_atomic(a.inventory,output)
    print(f'ASSET_AUDIT=PASS; VIDEOS=120; THUMBNAILS=120; FFMPEG_PROBED={checked}/120')
    print('INVENTORY='+str(Path(a.inventory).resolve()))
    if checked<120:print('QA_NOT_COMPLETE: Install ffprobe and rerun with --require-ffprobe before media signoff.')


def localhost_base(base):
    u=urlsplit(base)
    require(u.scheme=='http' and u.hostname in ('localhost','127.0.0.1','::1') and not u.username and not u.password and not u.query and not u.fragment and u.path in ('','/'),
            f'Only LOCAL HTTP Media API is allowed: {base}')
    return base.rstrip('/')


def api(method,url,token=None,obj=None,timeout=30):
    headers={'Accept':'application/json'}
    if token: headers['Authorization']='Bearer '+token
    data=None
    if obj is not None:
        headers['Content-Type']='application/json';data=json.dumps(obj,ensure_ascii=False).encode('utf-8')
    req=Request(url,method=method,headers=headers,data=data)
    try:
        with HTTP.open(req,timeout=timeout) as r:
            data=r.read(5_000_000)
            return json.loads(data.decode('utf-8'))
    except HTTPError as e:
        raise ValueError(f'HTTP {e.code} {method} {urlsplit(url).path}: {e.read(500).decode("utf-8",errors="replace")}') from e
    except URLError as e:
        raise ValueError(f'Không kết nối được service: {e.reason}') from e


def put_presigned(url,asset):
    u=urlsplit(url)
    require(u.scheme in ('http','https') and u.hostname,'Invalid presigned PUT URL')
    require(u.hostname in ('localhost','127.0.0.1','::1'),f'MinIO presigned host không phải local: {u.hostname}. Kiểm tra MINIO_ENDPOINT, không upload lên host khác.')
    port=u.port or (443 if u.scheme=='https' else 80)
    conn=(http.client.HTTPSConnection if u.scheme=='https' else http.client.HTTPConnection)(u.hostname,port,timeout=120)
    try:
        conn.putrequest('PUT',u.path+('?' + u.query if u.query else ''))
        conn.putheader('Content-Type',asset['mimeType'])
        conn.putheader('Content-Length',str(asset['sizeBytes']))
        conn.endheaders()
        with Path(asset['localPath']).open('rb') as f:
            while True:
                buf=f.read(1024*1024)
                if not buf:break
                conn.send(buf)
        response=conn.getresponse();status=response.status;info=response.read(400)
        require(status in (200,201,204),f'MinIO PUT failed: HTTP {status}: {info[:120]!r}')
    finally:conn.close()


def get_ready(base,mid,token,asset=None):
    try:res=api('GET',base+'/api/v1/media/'+mid,token)
    except ValueError as e:
        if 'HTTP 400' in str(e) or 'HTTP 500' in str(e) or 'HTTP 409' in str(e):return None
        raise
    if res.get('status')!='READY':return None
    if asset:
        require(res.get('originalFilename')==asset['fileName'],f'Media {mid}: originalFilename khác inventory')
        require(res.get('sizeBytes')==asset['sizeBytes'],f'Media {mid}: sizeBytes khác inventory')
        require(res.get('mimeType')==asset['mimeType'],f'Media {mid}: MIME khác inventory')
    require(isinstance(res.get('url'),str) and res['url'],'READY nhưng thiếu URL xem media')
    return res


def load_inventory(a):
    items,_=read_sources(a.repo)
    inv=json.loads(Path(a.inventory).read_text(encoding='utf-8'))
    require(inv['format']=='dts-simulation-asset-inventory-v1' and inv['jsonHashes']==EXPECTED_HASH,'Inventory không thuộc JSON V1 chốt')
    assets=inv['assets']
    require(len(assets)==240 and len(set((x['scenarioId'],x['type']) for x in assets))==240,'Inventory không đủ hoặc có trùng')
    for x in assets:
        path=Path(x['localPath'])
        require(path.is_file() and path.stat().st_size==x['sizeBytes'],f'Asset missing / size mismatch: {path}')
    return assets


def load_checkpoint(path,base):
    p=Path(path)
    if not p.exists():return {'format':'dts-simulation-media-map-v1','baseUrl':base,'jsonHashes':EXPECTED_HASH,'ready':{},'pending':{}}
    c=json.loads(p.read_text(encoding='utf-8'))
    require(c.get('format')=='dts-simulation-media-map-v1' and c.get('baseUrl')==base and c.get('jsonHashes')==EXPECTED_HASH,'Checkpoint khác environment hoặc JSON; DỪNG')
    require(isinstance(c.get('ready'),dict) and isinstance(c.get('pending'),dict),'Invalid checkpoint')
    return c


def key(x):return f"{x['scenarioId']:03d}/{x['type']}"


def cmd_upload(a):
    base=localhost_base(a.media_api)
    assets=load_inventory(a)
    checkpoint=Path(a.checkpoint).resolve()
    require(not str(checkpoint).startswith(str(Path(a.repo).resolve())), 'Checkpoint PHẢI để ngoài Git repo!')
    c=load_checkpoint(checkpoint,base)
    target=[x for x in assets if a.scenario is None or x['scenarioId']==a.scenario]
    require(target,'Không tìm thấy scenario được chọn')
    if not a.execute:
        todo=[x for x in target if key(x) not in c['ready']]
        print(f'MODE=DRY_RUN; TARGET_ASSETS={len(target)}; ALREADY_READY={len(target)-len(todo)}; WOULD_UPLOAD={len(todo)}')
        print('NO_NETWORK_WRITE=true; NO_FILE_CHANGE=true')
        return
    token=os.environ.get('DTS_MEDIA_TOKEN','').strip()
    require(bool(token),'Thiếu biến môi trường DTS_MEDIA_TOKEN (JWT người quản lý).')
    completed=0
    for x in target:
        k=key(x)
        if k in c['ready']:
            saved=c['ready'][k]
            require(saved.get('sha256')==x['sha256'],'Source file changed vs checkpoint; stop')
            require(get_ready(base,saved['mediaId'],token,x) is not None,f'{k}: media previously READY but no longer ready')
            continue
        if k in c['pending']:
            p=c['pending'][k]
            require(p.get('sha256')==x['sha256'],f'{k}: pending source changed')
            detail=get_ready(base,p['mediaId'],token,x)
            if detail:
                c['ready'][k]={'mediaId':p['mediaId'],'objectKey':p['objectKey'],'sha256':x['sha256'],'fileName':x['fileName']}
                del c['pending'][k];dump_atomic(checkpoint,c)
                print(f'RECOVERED_READY={k}: mediaId={p["mediaId"]}')
                continue
            raise ValueError(f'{k}: tồn tại PENDING upload session={p["sessionId"]}. Không tạo upload trùng; kiểm tra worker/MinIO rồi chạy lại.')
        if a.limit>0 and completed>=a.limit:break
        require(file_sha256(x['localPath'])==x['sha256'],f'{k}: SHA-256 file thay đổi sau audit')
        targetType='SIMULATION_VIDEO' if x['type']=='video' else 'SIMULATION_THUMBNAIL'
        form={'fileName':x['fileName'],'sizeBytes':x['sizeBytes'],'mimeType':x['mimeType'],'visibility':'PRIVATE','targetType':targetType}
        v=api('POST',base+'/api/v1/media/uploads/initialize',token,form)
        for field in ('mediaId','sessionId','objectKey','presignedUrl'):require(v.get(field),f'Initialize thiếu {field}')
        c['pending'][k]={'mediaId':v['mediaId'],'sessionId':v['sessionId'],'objectKey':v['objectKey'],'sha256':x['sha256']}
        dump_atomic(checkpoint,c)
        print(f'INITIALIZED={k}: mediaId={v["mediaId"]} objectKey={v["objectKey"]}',flush=True)
        put_presigned(v['presignedUrl'],x)
        api('POST',base+'/api/v1/media/uploads/'+v['sessionId']+'/confirm',token)
        result=None
        until=time.monotonic()+a.ready_wait
        while time.monotonic()<until:
            result=get_ready(base,v['mediaId'],token,x)
            if result:break
            time.sleep(2)
        require(result is not None,f'{k}: Media chưa READY sau {a.ready_wait}s; giữ pending, kiểm tra Kafka/worker rồi chạy lại')
        c['ready'][k]={'mediaId':v['mediaId'],'objectKey':v['objectKey'],'sha256':x['sha256'],'fileName':x['fileName']}
        del c['pending'][k];dump_atomic(checkpoint,c)
        completed+=1
        print(f'READY={k}: mediaId={v["mediaId"]}',flush=True)
    print(f'UPLOAD_FINISHED; NEW_READY={completed}; TOTAL_READY={len(c["ready"])}/240; PENDING={len(c["pending"])}')
    print('CHECKPOINT='+str(checkpoint))


def cmd_verify(a):
    base=localhost_base(a.media_api)
    assets=load_inventory(a)
    cp=load_checkpoint(a.checkpoint,base)
    selected=[x for x in assets if a.scenario is None or x['scenarioId']==a.scenario]
    expected=240 if a.scenario is None else 2
    require(len(selected)==expected, f'Selection incomplete: {len(selected)}')
    if a.scenario is None:
        require(len(cp['ready'])==240 and not cp['pending'],f'Map chưa đủ: ready={len(cp["ready"])}, pending={len(cp["pending"])}')
    token=os.environ.get('DTS_MEDIA_TOKEN','').strip()
    require(token,'Thiếu DTS_MEDIA_TOKEN')
    ready=0;ranged=0;hashed=0
    for x in selected:
        k=key(x);saved=cp['ready'].get(k)
        require(saved and saved['sha256']==x['sha256'],f'{k}: checkpoint thiếu/sai SHA')
        res=get_ready(base,saved['mediaId'],token,x)
        require(res is not None,f'{k}: Media không READY')
        ready+=1
        if x['type']=='video' and a.range_check:
            url=res['url'];u=urlsplit(url)
            require(u.hostname in ('127.0.0.1','localhost','::1'),'Video GET URL không trỏ local')
            req=Request(url,headers={'Range':'bytes=0-1'})
            with HTTP.open(req,timeout=30) as stream:
                require(stream.status==206,f'{k}: Range cần HTTP 206, nhận {stream.status}')
                stream.read(2)
            ranged+=1
        if a.full_sha_check:
            u=urlsplit(res['url'])
            require(u.hostname in ('127.0.0.1','localhost','::1'),f'{k}: signed URL not local')
            digest=hashlib.sha256()
            with HTTP.open(Request(res['url']),timeout=120) as stream:
                while True:
                    part=stream.read(4*1024*1024)
                    if not part:break
                    digest.update(part)
            require(digest.hexdigest()==x['sha256'],f'{k}: downloaded MinIO object SHA differs from local source')
            hashed+=1
    print(f'MEDIA_VERIFY=PASS; READY={ready}/{expected}; HTTP_RANGE_206={ranged}/{expected//2}; REMOTE_SHA256_VERIFIED={hashed}/{expected}')
    print('NOTE: Browser CORS, video decoding, scoring visual alignment and distribution rights still require separate QA.')

def main():
    p=argparse.ArgumentParser(description=__doc__);sub=p.add_subparsers(dest='command',required=True)
    i=sub.add_parser('install',help='Copy frozen JSON into existing Content Builder repo without overwrite')
    for a in (i,): a.add_argument('--repo',required=True)
    i.add_argument('--scenarios',required=True);i.add_argument('--sets',required=True);i.set_defaults(fn=cmd_install)
    v=sub.add_parser('validate-json');v.add_argument('--repo',required=True);v.set_defaults(fn=lambda a:read_sources(a.repo))
    a=sub.add_parser('audit-assets')
    a.add_argument('--repo',required=True);a.add_argument('--assets-root',required=True);a.add_argument('--inventory',required=True)
    a.add_argument('--require-ffprobe',action='store_true');a.add_argument('--duration-tolerance',type=float,default=0.75)
    a.set_defaults(fn=cmd_audit)
    u=sub.add_parser('upload')
    u.add_argument('--repo',required=True);u.add_argument('--inventory',required=True);u.add_argument('--checkpoint',required=True)
    u.add_argument('--media-api',default='http://127.0.0.1:8080');u.add_argument('--scenario',type=int,choices=range(1,121),metavar='1..120')
    u.add_argument('--limit',type=int,default=0);u.add_argument('--ready-wait',type=int,default=90);u.add_argument('--execute',action='store_true')
    u.set_defaults(fn=cmd_upload)
    m=sub.add_parser('verify-media');m.add_argument('--repo',required=True);m.add_argument('--inventory',required=True);m.add_argument('--checkpoint',required=True)
    m.add_argument('--media-api',default='http://127.0.0.1:8080');m.add_argument('--range-check',action='store_true');m.add_argument('--full-sha-check',action='store_true');m.add_argument('--scenario',type=int,choices=range(1,121),metavar='1..120');m.set_defaults(fn=cmd_verify)
    opts=p.parse_args()
    try:opts.fn(opts)
    except (ValueError, OSError, KeyError, json.JSONDecodeError) as e:
        print('FAILED: '+str(e),file=sys.stderr);sys.exit(2)

if __name__=='__main__':main()
