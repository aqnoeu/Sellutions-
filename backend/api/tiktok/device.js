const {device}=require('../../lib/tiktok');module.exports=(req,res)=>device(req,res,typeof req.query?.action==='string'?req.query.action:'');
