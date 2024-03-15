<%--
/*------------------------------------------------------------------------------
/* Name : ATNAjaxProgress.jsp
/* DESC : 리포트 출력시 진행상황 세션값 반환
/* VER. : 1.0
/* AUTHOR : DK.KIM
/* PROJ : AUTONICS
/* 
/* Copyright 2014 by ENOVIA All rights reserved.
/*------------------------------------------------------------------------------
/*                Revision history
/*------------------------------------------------------------------------------
/*  DATE          Author            Description
/* ----------   ---------------------    ---------------------------
/* 2016.01.11   DK.KIM                    First Creation
------------------------------------------------------------------------------*/
--%>
<%@page import="com.atn.common.ATNDomainUtil"%>
<%@page import="com.atn.common.ATNSessionUtil"%>
<% response.setContentType("text/html; charset=UTF-8"); %>
<%
try {   
    
    String sessTimeName = request.getParameter("SessTimeName");    
    String strProgress = "";
    try {
        if(ATNSessionUtil.containsKey(sessTimeName)) {
            String strTxtKey = sessTimeName + "TXT";
            String strTxt = "";
            if(ATNSessionUtil.containsKey(strTxtKey)) {
                strTxt = (String)ATNSessionUtil.get(strTxtKey);
            }
            strProgress = ATNDomainUtil.getProgressValue(sessTimeName);
            int iProgress = ("".equals(strProgress)?0:Integer.valueOf(strProgress));
            if(iProgress > 99) {
                String strTotalName = sessTimeName + "T";
                String strCurrentName = sessTimeName + "C";
                ATNSessionUtil.remove(sessTimeName);
                ATNSessionUtil.remove(strTotalName);
                ATNSessionUtil.remove(strCurrentName);
            }
            strProgress = strProgress + "^" + strTxt; 
        }
    } catch(Exception ex) {
        strProgress = "ERROR";
    }
    out.clear();
    out.println(strProgress);   
} catch (Exception e) {
    out.clear();
}
%>