const {cfg,storageCheck}=require('../lib/tiktok');
module.exports = async function handler(req,res){
  res.setHeader('Cache-Control','no-store');
  if(req.method!=='GET'){res.setHeader('Allow','GET');return res.status(405).json({error:'method_not_allowed'});}
  let storage='not_configured';
  if(req.query?.check==='storage'){
    try{const c=cfg(process.env,false);storage=await storageCheck(c)?'verified':'failed';}catch{storage='failed';}
  }else{
    try{cfg(process.env,false);storage='configured';}catch{}
  }
  return res.status(200).json({service:'Sellutions',status:'ok',integrations:{tiktok:process.env.TIKTOK_PILOT_ENABLED==='true'?'pilot_enabled':'pending',shopee:'pending'},storage});
};