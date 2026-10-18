#!/usr/bin/env python3
"""Compare resolved visible-surface coverage; this is not a frame timing report."""
import argparse,json,math,statistics,csv,gzip
from pathlib import Path
p=argparse.ArgumentParser();p.add_argument('--before',required=True);p.add_argument('--after',required=True);p.add_argument('--out',required=True);a=p.parse_args()
before=json.loads(Path(a.before).read_text());after=json.loads(Path(a.after).read_text())
if before['fixtureSha256']!=after['fixtureSha256']:raise SystemExit('Visual fixtures differ; no matched comparison')
def indexed(report):
 rows={r['sample']:r for r in report['samples']}
 if set(rows)!=set(range(166)):raise SystemExit('Incomplete dense route')
 return rows
b=indexed(before);c=indexed(after)
camera_mismatches=[]
for i in b:
 if b[i]['segment']!=c[i]['segment']:raise SystemExit('Segment mismatch at '+str(i))
 if any(abs(x-y)>.001 for x,y in zip(b[i]['eye'],c[i]['eye'])):
  if b[i]['segment'] in ('coldFlight','warmReversal'):raise SystemExit('Cold flight/reversal camera mismatch at '+str(i))
  camera_mismatches.append({'sample':i,'segment':b[i]['segment'],'before':b[i]['eye'],'after':c[i]['eye']})
def ratio(r):return r['near24Covered']/r['near24Eligible'] if r['near24Eligible'] else None
def stats(rows):
 eligible=sum(r['near24Eligible'] for r in rows);ratios=[ratio(r) for r in rows if ratio(r) is not None];longest=run=0
 for r in rows:
  run=run+1 if ratio(r)==0 else 0;longest=max(longest,run)
 return {'frames':len(rows),'zeroEligibleFrames':sum(r['near24Eligible']==0 for r in rows),'aggregateNearCoverage':sum(r['near24Covered'] for r in rows)/eligible if eligible else None,'medianFrameCoverage':statistics.median(ratios) if ratios else None,'minimumFrameCoverage':min(ratios) if ratios else None,'zeroCoverageFrames':sum(x==0 for x in ratios),'longestZeroRun':longest}
def sources(rows):
 result={}
 for name,numerator,denominator in [('nearSky','near24SkyKnown','near24Samples'),('nearSource','near24SourcePresent','near24Samples'),('detailSky','detailed112SkyKnown','detailed112Samples'),('detailSource','detailed112SourcePresent','detailed112Samples'),('allSource','surfaceSourcePresent','surfaceSamples')]:
  if not all(numerator in r for r in rows):result[name]=None;continue
  count=sum(r[denominator] for r in rows);values=[r[numerator]/r[denominator] for r in rows if r[denominator]]
  result[name]={'available':sum(r[numerator] for r in rows),'samples':count,'aggregate':sum(r[numerator] for r in rows)/count if count else None,'minimumFrame':min(values) if values else None,'framesWithMissingSource':sum(r[numerator]<r[denominator] for r in rows)}
 return result
def surface_probes(report,sample):
 path=Path(report);path=path.with_name(path.name.removesuffix('-report.json')+'-surface-'+str(sample)+'.csv.gz')
 if not path.exists():return None
 with gzip.open(path,'rt') as stream:return {int(r['pixel']):r for r in csv.DictReader(stream)}
def rendered(indices):
 frames=[];all_ratios=[];near_ratios=[];far_ratios=[];horizontal_far_ratios=[];voxel_face_ratios=[];near_face_ratios=[]
 for sample in indices:
  left,right=surface_probes(a.before,sample),surface_probes(a.after,sample)
  if left is None or right is None:return {'available':False,'reason':'Historical fixture has no rendered surface RGB probes'}
  values=[];near=[];far=[];horizontal_far=[];voxel_face=[];near_face=[]
  for pixel,old in left.items():
   new=right.get(pixel)
   if new is None:continue
   strict=max(abs(float(old[k])-float(new[k])) for k in ('x','y','z'))<=1/32
   is_far=float(old['range'])>24 and float(new['range'])>24
   horizontal=is_far and abs(float(old['y'])-float(new['y']))<=1/32 and max(abs(float(old[k])-float(new[k])) for k in ('x','z'))<=.25 and all(math.floor(float(old[k]))==math.floor(float(new[k])) for k in ('x','z')) and all(abs(float(r['y'])-round(float(r['y'])))<=1/32 and all(.1<float(r[k])-math.floor(float(r[k]))<.9 for k in ('x','z')) for r in (old,new))
   face=False
   if True:
    for axis in ('x','y','z'):
     tangents=[k for k in ('x','y','z') if k!=axis]
     if abs(float(old[axis])-float(new[axis]))<=1/32 and round(float(old[axis]))==round(float(new[axis])) and all(abs(float(r[axis])-round(float(r[axis])))<=1/32 and all(.1<float(r[k])-math.floor(float(r[k]))<.9 for k in tangents) for r in (old,new)) and all(abs(float(old[k])-float(new[k]))<=.25 and math.floor(float(old[k]))==math.floor(float(new[k])) for k in tangents):face=True;break
   if not strict and not horizontal and not face:continue
   before_luma=sum(float(old[k])*weight for k,weight in [('r',.2126),('g',.7152),('b',.0722)])
   after_luma=sum(float(new[k])*weight for k,weight in [('r',.2126),('g',.7152),('b',.0722)])
   if before_luma<=.00001:continue
   ratio=after_luma/before_luma
   if horizontal:horizontal_far.append(ratio)
   if face and is_far:voxel_face.append(ratio)
   if face and strict and float(old['range'])<=24 and float(new['range'])<=24:near_face.append(ratio)
   if not strict:continue
   values.append(ratio)
   if is_far:far.append(ratio)
   if float(old['range'])<=24 and float(new['range'])<=24:near.append(ratio)
  all_ratios.extend(values);near_ratios.extend(near);far_ratios.extend(far);horizontal_far_ratios.extend(horizontal_far);voxel_face_ratios.extend(voxel_face);near_face_ratios.extend(near_face)
  frames.append({'sample':sample,'beforeProbes':len(left),'afterProbes':len(right),'matchedSurfaceProbes':len(values),'nearMatchedSurfaceProbes':len(near),'farMatchedSurfaceProbes':len(far),'farHorizontalSameVoxelProbes':len(horizontal_far),'farVoxelFaceProbes':len(voxel_face),'farVoxelFaceMedianLuminanceRatio':statistics.median(voxel_face) if voxel_face else None,'farVoxelFaceFractionDarkenedOver10Percent':sum(v<.9 for v in voxel_face)/len(voxel_face) if voxel_face else None,'beforeFarProbes':sum(float(r['range'])>24 for r in left.values()),'afterFarProbes':sum(float(r['range'])>24 for r in right.values()),'medianLuminanceRatio':statistics.median(values) if values else None,'fractionDarkenedOver10Percent':sum(v<.9 for v in values)/len(values) if values else None,'nearMedianLuminanceRatio':statistics.median(near) if near else None,'farHorizontalSameVoxelMedianLuminanceRatio':statistics.median(horizontal_far) if horizontal_far else None,'farHorizontalSameVoxelFractionDarkenedOver10Percent':sum(v<.9 for v in horizontal_far)/len(horizontal_far) if horizontal_far else None,'nearFractionDarkenedOver10Percent':sum(v<.9 for v in near)/len(near) if near else None})
 def distribution(values):
  values=sorted(values)
  return {'count':len(values),'p01':values[math.ceil(len(values)*.01)-1] if values else None,'p05':values[math.ceil(len(values)*.05)-1] if values else None,'median':statistics.median(values) if values else None,'fractionDarkenedOver10Percent':sum(v<.9 for v in values)/len(values) if values else None,'fractionDarkenedOver25Percent':sum(v<.75 for v in values)/len(values) if values else None}
 return {'available':True,'method':'Actual resolved linear HDR RGB before UI/postprocessing, stride12. Same-pixel probes match only when all world-coordinate differences are at most1/32m. Report unmatched/zero-match frames; never count them as unchanged. Ratios are candidate/baseline luminance; geometry, water, shadows and GI can legitimately change color, so ratios accompany source coverage and rendered image inspection rather than imply radiance equivalence.','detail':distribution(all_ratios),'near':distribution(near_ratios),'farStrict':distribution(far_ratios),'farHorizontalSameVoxel':distribution(horizontal_far_ratios),'farVoxelFace':distribution(voxel_face_ratios),'nearVoxelFace':distribution(near_face_ratios),'farVoxelFaceMethod':'Far natural voxel faces must share the same integer face plane within1/32m, match the other two axes within1/4m in the central80percent of the same integer voxel, and retain camera-side face orientation. This is a coarse face-color check, not subvoxel equivalence.','zeroFarVoxelFaceFrames':sum(f['farVoxelFaceProbes']==0 for f in frames),'noFarSurfaceFrames':sum(f['beforeFarProbes']==0 and f['afterFarProbes']==0 for f in frames),'unmatchedFarFramesWithSurface':sum(f['farVoxelFaceProbes']==0 and f['beforeFarProbes']>0 and f['afterFarProbes']>0 for f in frames),'farHorizontalMethod':'Far natural horizontal surfaces must lie within1/32m of the same integer Y plane, inside the central80percent of the same integer X/Z voxel, and match within1/32m vertically and1/4m horizontally. This excludes vertical faces and voxel edges. This broader terrain check does not qualify subvoxel equivalence; strict1/32m results are retained separately.','zeroFarHorizontalFrames':sum(f['farHorizontalSameVoxelProbes']==0 for f in frames),'zeroMatchedFrames':sum(f['matchedSurfaceProbes']==0 for f in frames),'frames':frames}
segments={}
for seg in sorted({r['segment'] for r in b.values()}):
 indices=[i for i in sorted(b) if b[i]['segment']==seg];drops=[];changes=[]
 for i in indices:
  if any(r['sample']==i for r in camera_mismatches):continue
  br,cr=ratio(b[i]),ratio(c[i])
  if br is not None and cr is not None:changes.append(cr-br)
  if br is not None and cr is not None and cr<br-.02:drops.append({'sample':i,'before':br,'after':cr,'difference':cr-br})
 segments[seg]={'sourceAvailabilityBefore':sources([b[i] for i in indices]),'sourceAvailabilityAfter':sources([c[i] for i in indices]),'before':stats([b[i] for i in indices]),'after':stats([c[i] for i in indices]),'worstMatchedFrameCoverageChange':min(changes) if changes else None,'matchedFramesWithLowerCoverage':sum(d<0 for d in changes),'framesDroppingMoreThanTwoPercentagePoints':sorted(drops,key=lambda r:r['difference'])}
for seg in ('coldFlight','warmReversal'):
 segments[seg]['renderedSurfaceRGB']=rendered([i for i in sorted(b) if b[i]['segment']==seg])
out={'method':'Every frame uses actual resolved visible depth unprojected through its jittered camera. Near surface eligibility is within24m horizontally and fixed GI vertical range. Coverage is inside the complete active GPU field; exact GPU payloads checked separately. Frames with no eligible surface are reported, never counted as covered. No FPS claim.','fixtureSha256':before['fixtureSha256'],'beforeRevision':before['revision'],'afterRevision':after['revision'],'cameraMismatchesOutsideColdFlightAndReversal':camera_mismatches,'lightingSourceMethod':'Availability means a complete active GI field contains the resolved surface or its exact sky column is present, following voxel shader lookup. This is an availability check, not radiance equivalence; occlusion/readback and rendered screenshots are checked separately.','sourceAvailabilityCaveat':'Column presence is queried at the depth-unprojected world point, without per-fragment normal bias. Depth precision and voxel/chunk edges can differ from the actual shader sample. These counts describe data footprint, not visible darkness or radiance equivalence; actual resolved RGB and exact GPU lookup/readback checks accompany them.','qualificationScope':'Cold flight80 and reversal16 frames have matched eye poses and fixture hashes. Jump/teleport safety-boundary camera differences are listed; those segments have raw statistics only, not matched visual qualification.','segments':segments}
Path(a.out).write_text(json.dumps(out,indent=2)+'\n')
for seg in ('coldFlight','warmReversal'):print(seg,json.dumps(segments[seg]))
