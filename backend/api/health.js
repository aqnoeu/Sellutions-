const {cfg}=require('../lib/tiktok');
module.exports = function handler(req,res){
  res.setHeader('Cache-Control','no-store');
  if(req.method!=='GET'){res.setHeader('Allow','GET');return res.status(405).json({error:'method_not_allowed'});}
  let storage='not_configured',tiktok='pending';
  try{cfg(process.env,false);storage='configured';cfg();tiktok='pilot_enabled';}catch{}
  return res.status(200).json({service:'Sellutions',status:'ok',integrations:{tiktok,shopee:'pending'},storage});
};
