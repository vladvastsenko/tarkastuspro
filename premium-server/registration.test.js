const test = require("node:test");
const assert = require("node:assert/strict");
const {hash,secret,issuePermit,consumePermit} = require("./registration");
const {product} = require("./policy");
function database(){
 const rows=new Map();
 let queue=Promise.resolve();
 return {collection:name=>({doc:id=>({id,key:name+"/"+id})}),runTransaction(fn){
 const run=queue.then(async()=>{const next=new Map(rows);const result=await fn({
 get:async ref=>({exists:next.has(ref.key),data:()=>next.get(ref.key)}),
 create:(ref,data)=>{if(next.has(ref.key))throw Error("exists");next.set(ref.key,data);},
 update:(ref,data)=>next.set(ref.key,{...next.get(ref.key),...data})
 });for(const [k,v] of next)rows.set(k,v);return result;});queue=run.catch(()=>{});return run;
 }};
}
const purchase=(session,state="ACTIVE")=>({subscriptionState:"SUBSCRIPTION_STATE_"+state,externalAccountIdentifiers:{obfuscatedExternalAccountId:hash(session)},lineItems:[{productId:product,offerDetails:{basePlanId:"monthly"},autoRenewingPlan:{},expiryTime:new Date(1000000).toISOString()}]});
test("reject pending and mismatched purchases",async()=>{const db=database(),s=secret();await assert.rejects(issuePermit(db,"token",s,purchase(s,"PENDING"),1));await assert.rejects(issuePermit(db,"token",s,purchase(secret()),1));});
test("concurrent issuance recovers same permit but only one consume succeeds",async()=>{const db=database(),s=secret();const issued=await Promise.allSettled([issuePermit(db,"token",s,purchase(s),1),issuePermit(db,"token",s,purchase(s),1)]);assert.equal(issued.filter(x=>x.status==="fulfilled").length,2);assert.equal(issued[0].value,issued[1].value);const p=issued[0].value;const used=await Promise.allSettled([consumePermit(db,p,s,"uid1",2),consumePermit(db,p,s,"uid2",2)]);assert.equal(used.filter(x=>x.status==="fulfilled").length,1);});
test("wrong session and expired permit denied",async()=>{const db=database(),s=secret();const p=await issuePermit(db,"token",s,purchase(s),1);await assert.rejects(consumePermit(db,p,secret(),"uid",2));await assert.rejects(consumePermit(db,p,s,"uid",600001));});
test("expired permit recovery requires fresh purchase verification",async()=>{const db=database(),s=secret();const p=await issuePermit(db,"token",s,purchase(s),1);const q=await issuePermit(db,"token",s,purchase(s),600002);assert.equal(p,q);await assert.rejects(consumePermit(db,p,s,"uid",600003));const {reserveAccount}=require("./registration");const uid=await reserveAccount(db,q,s,"a@example.com",600003,660003);assert.equal(await issuePermit(db,"token",s,purchase(s),600004),q);assert.equal(await reserveAccount(db,q,s,"a@example.com",600005,660005),uid);await assert.rejects(reserveAccount(db,q,s,"b@example.com",600005,660005));});

test("account reservation survives retry but rejects changed email or session",async()=>{
 const {reserveAccount}=require("./registration"),db=database(),s=secret();
 const p=await issuePermit(db,"token",s,purchase(s),1);
 const uid=await reserveAccount(db,p,s,"example@example.com",2);
 assert.match(uid,/^premium_[a-f0-9]{64}$/);
 assert.equal(await reserveAccount(db,p,s,"EXAMPLE@example.com",600002),uid);
 await assert.rejects(reserveAccount(db,p,s,"other@example.com",3));
 await assert.rejects(reserveAccount(db,p,secret(),"example@example.com",3));
});
test("cancelled and pending subscriptions cannot reserve a new account",async()=>{
 const db=database(),s=secret();
 for(const state of ["CANCELED","PENDING","EXPIRED","PAUSED","ON_HOLD"])
   await assert.rejects(issuePermit(db,"token",s,purchase(s,state),1));
});
test("server bound purchase hash verifies for reserved UID",()=>{
 const {assess}=require("./policy"),s=secret(),uid="premium_"+secret();
 assert.equal(assess(purchase(s),uid,1,hash(s)).uid,uid);
 assert.equal(assess(purchase(s),uid,1,hash(s)).eligible,true);
 assert.throws(()=>assess(purchase(s),uid,1,hash(secret())));
});

test("expired permit requires fresh verification and correct session",async()=>{
 const {reserveAccount}=require("./registration"),db=database(),s=secret();
 const p=await issuePermit(db,"token",s,purchase(s),1);
 await assert.rejects(reserveAccount(db,p,s,"a@example.com",600002));
 await assert.rejects(reserveAccount(db,p,secret(),"a@example.com",600002,660002));
 await assert.rejects(reserveAccount(db,p,s,"a@example.com",600002,600002));
 assert.match(await reserveAccount(db,p,s,"a@example.com",600002,660002),/^premium_/);
});

test("Google registration binds purchase to provider subject, not another identity or email", async () => {
 const {reserveAccount}=require("./registration"),db=database(),s=secret();
 const p=await issuePermit(db,"google-token",s,purchase(s),1);
 const uid=await reserveAccount(db,p,s,"google:123456",2);
 assert.equal(await reserveAccount(db,p,s,"google:123456",3),uid);
 await assert.rejects(reserveAccount(db,p,s,"google:987654",3));
 await assert.rejects(reserveAccount(db,p,s,"other@example.com",3));
 await assert.rejects(reserveAccount(db,p,secret(),"google:123456",3));
});
