const test = require("node:test");
const assert = require("node:assert/strict");
const {assess,accountHash,product} = require("./policy");
const make = state => ({subscriptionState:"SUBSCRIPTION_STATE_"+state,externalAccountIdentifiers:{obfuscatedExternalAccountId:accountHash("a")},lineItems:[{productId:product,offerDetails:{basePlanId:"monthly"},autoRenewingPlan:{},expiryTime:new Date(20000).toISOString()}]});
test("active and cancelled still valid, pending and held denied",()=>{for(const state of ["ACTIVE","CANCELED","IN_GRACE_PERIOD","PENDING","ON_HOLD","EXPIRED","PAUSED"]) assert.equal(assess(make(state),"a",10000).eligible,["ACTIVE","CANCELED","IN_GRACE_PERIOD"].includes(state));});
test("wrong account or plan rejected",()=>{assert.throws(()=>assess(make("ACTIVE"),"b",10000));const p=make("ACTIVE");p.lineItems[0].offerDetails.basePlanId="other";assert.throws(()=>assess(p,"a",10000));});
test("expiry denied",()=>assert.equal(assess(make("ACTIVE"),"a",20000).eligible,false));
