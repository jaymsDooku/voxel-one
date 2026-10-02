import { database } from './storage.mjs';
function json(value,status=200){return Response.json(value,{status,headers:{'Cache-Control':'no-store','X-Content-Type-Options':'nosniff'}});}
async function body(request){if(!request.headers.get('content-type')?.startsWith('application/json'))throw new Error('Invalid request format');if(Number(request.headers.get('content-length')||0)>16000)throw new Error('Request too long');const text=await request.text();if(text.length>16000)throw new Error('Request too long');return JSON.parse(text);}
function unpack(row){return {id:row.id,version:row.version,title:row.title,context:row.context,options:JSON.parse(row.options),status:row.status,createdAt:row.created_at,updatedAt:row.updated_at};}
export default {async fetch(request,env){const url=new URL(request.url);if(!url.pathname.startsWith('/api/'))return env.ASSETS.fetch(request);
 const user=request.headers.get('oai-authenticated-user-id');
 // This Site stays owner-private. Dispatch checks and consumes the Sites service credential.
 // A service caller has no browser identity; it must also explicitly select the agent API.
 const agent=!user&&request.headers.get('x-voxel-agent')==='1';
 if(!user&&!agent)return json({error:'Sign in to this dashboard to answer questions.'},401);
 try{const db=database(env);
 if(url.pathname==='/api/questions'&&request.method==='GET'&&user){const qs=await db.prepare('SELECT * FROM questions ORDER BY created_at DESC LIMIT 100').all();const rows=await db.prepare('SELECT question_id,question_version,answer,updated_at FROM question_answers WHERE user_id = ?').bind(user).all();const answers=new Map(rows.results.map(r=>[r.question_id,r]));return json({schemaVersion:1,questions:qs.results.map(r=>({...unpack(r),saved:answers.get(r.id)?.question_version===r.version?answers.get(r.id):null}))});}
 if(url.pathname==='/api/answers'&&request.method==='POST'&&user){
  if(request.headers.get('origin')!==url.origin)return json({error:'Please submit your answer from this dashboard.'},403);
  let value;try{value=await body(request);}catch{return json({error:'Invalid answer.'},400);}
  const question=await db.prepare('SELECT version,status FROM questions WHERE id = ?').bind(String(value.questionId||'')).first();
  if(!question||question.version!==value.version||question.status!=='open')return json({error:'This question changed or closed. Refresh questions before answering.'},409);
  if(typeof value.answer!=='string'||!value.answer.trim()||value.answer.length>4000)return json({error:'Enter an answer of 1 to 4000 characters.'},400);
  const updatedAt=new Date().toISOString();await db.prepare('INSERT INTO question_answers (question_id,question_version,answer,updated_at,user_id) VALUES (?,?,?,?,?) ON CONFLICT(question_id,user_id) DO UPDATE SET question_version=excluded.question_version,answer=excluded.answer,updated_at=excluded.updated_at').bind(value.questionId,value.version,value.answer.trim(),updatedAt,user).run();return json({saved:true,answer:value.answer.trim(),updatedAt});
 }
 if(url.pathname==='/api/agent/questions'&&agent){
  if(request.method==='GET'){const rows=await db.prepare('SELECT q.*,a.answer,a.question_version,a.updated_at AS answered_at FROM questions q LEFT JOIN question_answers a ON a.question_id=q.id AND a.question_version=q.version ORDER BY q.created_at DESC LIMIT 100').all();return json({questions:rows.results.map(r=>({...unpack(r),answer:r.answer||null,answeredAt:r.answered_at||null}))});}
  if(request.method==='POST'){let value;try{value=await body(request);}catch{return json({error:'Invalid question.'},400);}
   if(value.action==='resolve'){await db.prepare('UPDATE questions SET status=?,updated_at=? WHERE id=?').bind('resolved',new Date().toISOString(),String(value.id)).run();return json({resolved:true});}
   const q=value.question;if(value.action!=='ask'||!q||!/^[a-z0-9-]{1,80}$/.test(q.id)||typeof q.title!=='string'||!q.title.trim()||q.title.length>500||typeof q.context!=='string'||q.context.length>2000||!Array.isArray(q.options)||q.options.length>6||q.options.some(o=>typeof o!=='string'||o.length>200))return json({error:'Invalid question.'},400);
   const existing=await db.prepare('SELECT * FROM questions WHERE id=?').bind(q.id).first();const options=JSON.stringify(q.options);const changed=existing&&(existing.title!==q.title||existing.context!==q.context||existing.options!==options);const version=existing?existing.version+(changed?1:0):1;const now=new Date().toISOString();
   await db.prepare('INSERT INTO questions (id,version,title,context,options,status,created_at,updated_at) VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET version=excluded.version,title=excluded.title,context=excluded.context,options=excluded.options,status=excluded.status,updated_at=excluded.updated_at').bind(q.id,version,q.title,q.context,options,'open',existing?.created_at||now,now).run();return json({id:q.id,version,status:'open'});
  }
 }
 return json({error:'Not found'},404);
 }catch(error){console.error('Dashboard questions unavailable:',error.message);return json({error:'Questions could not be loaded or saved. Your draft is preserved; please try again.'},503);}
}};
