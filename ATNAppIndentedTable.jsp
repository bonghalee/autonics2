<%@page import="com.atn.common.ATNStringUtil"%>
<%@include file="../emxContentTypeInclude.inc" %>
<%@include file="emxNavigatorBaseInclude.inc" %>
<%@include file="emxUIConstantsInclude.inc" %>

<%@page import="com.matrixone.apps.domain.util.*" %>
<%@page import="org.apache.commons.lang3.StringUtils" %>

<%@page import="com.atn.common.ATNCommonUtil" %>
<%
    try
    {
        int intRowHeight            = 29;
        String filterFramePage      = ATNStringUtil.NVL( emxGetParameter( request, "FilterFramePage" ) );
        String filterFrameCount     = ATNStringUtil.NVL( emxGetParameter( request, "FilterFrameSize" ), "0" );
        String filterFrameProcess   = ATNStringUtil.NVL( emxGetParameter( request, "FilterFrameProcess" ), "../common/evdmAppIndentedTableFilterProcess.jsp" );
        String stringResourceFile   = UINavigatorUtil.getStringResourceFileId( context, emxGetParameter( request, "suiteKey" ) );
        String  header              = emxGetParameter(request, "header");
        boolean hideHeader          = Boolean.parseBoolean( emxGetParameter( request, "hideOuterHeader" ) );
        String  topPixel            = hideHeader ? "0px" : "35px";
        String languageStr          = request.getHeader( "Accept-Language" );
        String pageHeading          = StringUtils.isNotEmpty( header ) ? i18nNow.getI18nString( header, stringResourceFile, languageStr ) : "Indented View";
        String urlParameter         = ATNCommonUtil.convHttpRequestToQueryString( request );
        String indentedTableUrl = "../common/emxIndentedTable.jsp";
        
        filterFramePage += "?" + urlParameter;
        filterFrameProcess += "?" + urlParameter;
        indentedTableUrl += "?" + urlParameter;
%>
<%@include file="../emxUICommonHeaderBeginInclude.inc" %>
<title><%=EnoviaResourceBundle.getProperty( null, "emxFramework.Common.defaultPageTitle" ) %>
</title>
<link rel="stylesheet" href="../common/styles/emxUICalendar.css"></link>
<script language="JavaScript" src="../common/scripts/emxUIConstants.js"></script>
<script language="JavaScript" src="../common/scripts/emxUICore.js"></script>
<script type="text/javascript" src="../common/scripts/emxUICoreMenu.js"></script>
<script type="text/javascript" src="../common/scripts/emxUICalendar.js"></script>
<script language="JavaScript" src="../common/scripts/jquery-latest.js"></script>
<script language="javascript" src="../common/scripts/jquery-ui.min-xparam.js"></script>

<script language="Javascript">
    addStyleSheet("emxUIDefault");
    addStyleSheet("emxUIToolbar");
    addStyleSheet("emxUIMenu");
    addStyleSheet("emxUIDOMLayout");
    addStyleSheet("emxUIDialog");
    addStyleSheet("emxUIChannelDefault");
    addStyleSheet("emxUIList");
    addStyleSheet("emxUIForm");
    
    if (getTopWindow().isMobile)
        addStyleSheet("emxUIMobile", "mobile/styles/");
    
    var linkArray = new Array;
    var rowHeight = 0;
    var rowHeights = [];
    var pageNumber = 1;
    var pages = 1;
    var idArray = new Array();
    var busIdArray = new Array();
    var contentArray = new Array();
    var scrollLoc = 0;
    var filterFrameCount = <%=XSSUtil.encodeForJavaScript(context, filterFrameCount) %>;
    var filterFrameSize = 0;
    var totalFilterFrameHeight = 0;
    var paddingSize = 20;
    
    function reloadSize() {
        var divPageBodyHeight = document.getElementById("divPageBody").clientHeight;
        
        var divFilterDivisionHeight = filterFrameSize;
        var divFilterDivisionTop = divFilterDivisionHeight + 2;
        if ($("#divFilterDivision").is(':visible')) {
            $("#divFilterDivision").css("top", divFilterDivisionTop + "px");
            divPageBodyHeight = divPageBodyHeight - divFilterDivisionHeight - ($("#divFilterDivision").height() + 5);
            $("#tableFrame").css("top", (divFilterDivisionHeight + paddingSize) + "px");
            document.getElementById("tableFrame").style.height = divPageBodyHeight + 2 + "px";
        } else {
            divPageBodyHeight = divPageBodyHeight - divFilterDivisionHeight;
            $("#tableFrame").css("top", (divFilterDivisionTop + 8) + "px");
            document.getElementById("tableFrame").style.height = divPageBodyHeight - 8 + "px";
        }
    }
    
    function doFilter()
    {
        var formObject = findFrame(window, "filterFrame").document.forms[0];
        for (var i = 0; i < formObject.elements.length; i++) {
            var elementsName = formObject.elements[i].name;
            if (elementsName != null && elementsName != undefined && elementsName != "") {
                removeParameter(elementsName);
            }
        }
        
        var tableFrame = findFrame(window, "tableFrame");
        if(tableFrame && tableFrame.turnOnProgress)
            tableFrame.turnOnProgress();
        
        var filterTimeStamp = (tableFrame.document.getElementsByName("timeStamp")[0] != null) ? tableFrame.document.getElementsByName("timeStamp")[0].value : "";
        formObject.method = "post";
        formObject.target = "pagehidden";
        formObject.action = "<%=filterFrameProcess %>&filterTimeStamp=" + filterTimeStamp + "&filter=true";
        formObject.submit();
    }
    
    function onLoad() {
        initTableFrame();
        turnOffProgress();
        var trFilterObject = $("#filterFrame").contents().find("table tr");
        rowHeight = trFilterObject.height();
        
        $.each(trFilterObject, function (idx, element) {
            rowHeights.push($(element).height());
            
            if (filterFrameCount >= idx + 1)
                filterFrameSize += $(element).height();
            
            totalFilterFrameHeight += $(element).height();
        });
        $("#filterFrame").height(filterFrameSize);
        
        var intTempCount = trFilterObject.length;
        
        if (intTempCount <= filterFrameCount) {
            $("#divFilterDivision").hide();
        } else {
            $("#divFilterDivision").show();
            
            var colSpanCount = 0;
            var trObject = $("#filterFrame").contents().find("table tr");
            $(trObject).each(function (e) {
                var thisColLength = $(this).find("td").length;
                
                if (colSpanCount <= thisColLength) {
                    colSpanCount = thisColLength;
                }
            });
            
        }
        reloadSize();
        //search Area
        $('.schClose button').on("click", function () {
            if (!$(this).hasClass('on')) {
                if (rowHeights.length >= 1) {
                    
                    document.getElementById("filterFrame").style.height = totalFilterFrameHeight + "px";
                    var divPageBodyHeight = document.getElementById("divPageBody").clientHeight;
                    divPageBodyHeight = divPageBodyHeight - totalFilterFrameHeight - ($("#divFilterDivision").height() + 5);
                    document.getElementById("tableFrame").style.height = divPageBodyHeight + paddingSize + "px";
                    
                    reloadSize();
                    var divFilterDivisionHeight = $("#filterFrame").height();
                    var divFilterDivisionTop = divFilterDivisionHeight + 2;
                    $("#divFilterDivision").css("top", Number(divFilterDivisionTop));
                    $("#tableFrame").css("top", (divFilterDivisionHeight + paddingSize) + "px");
                }
                
                $(this).addClass('on');
            } else {
                document.getElementById("filterFrame").style.height = filterFrameSize + "px";
                var divPageBodyHeight = document.getElementById("divPageBody").clientHeight;
                divPageBodyHeight = divPageBodyHeight - filterFrameSize - ($("#divFilterDivision").height() + 5);
                
                reloadSize();
                
                var divFilterFrameHeight = $("#filterFrame").height();
                var divFilterDivisionTop = divFilterFrameHeight + 2;
                var divFilterDivisionHeight = $("#divFilterDivision").height();
                document.getElementById("tableFrame").style.height = divPageBodyHeight + paddingSize + "px";
                $("#divFilterDivision").css("top", divFilterDivisionTop);
                $("#tableFrame").css("top", divFilterFrameHeight + divFilterDivisionHeight);
                
                $(this).removeClass('on');
            }
            return false;
        });
    }
    
    function initTableFrame() {
        var filterFrame = findFrame(window, "filterFrame");
        filterFrame.document.forms[0].method = "post";
        filterFrame.document.forms[0].target = "tableFrame";
        filterFrame.document.forms[0].action = "<%=indentedTableUrl%>";
        filterFrame.document.forms[0].submit();
    }
    
    function removeParameter(parm) {
        var tableFrame = findFrame(window, "tableFrame");
        var urlParameters = tableFrame.urlParameters || "";
        
        if (urlParameters.indexOf("amp;") >= 0) {
            while (urlParameters.indexOf("amp;") >= 0) {
                urlParameters = urlParameters.replace("amp;", "");
            }
        }
        
        var arrURLparms = urlParameters.split("&");
        var len = arrURLparms.length;
        var count = 0;
        var newArrayUrlparams = new Array();
        for (var i = 0; i < len; i++) {
            arrURLparms[i] = arrURLparms[i].split("=");
            if (arrURLparms[i][0] != parm) {
                newArrayUrlparams[count] = arrURLparms[i].join("=");
                count++;
            }
        }
        tableFrame.urlParameters = newArrayUrlparams.join("&");
    }
</script>
</header>
<body class="no-footer" onload="javascript:onLoad();" onresize="javascript:reloadSize();" style="overflow: hidden;">
    <emxUtil:ifExpr expr="<%=!hideHeader %>">
        <div id="pageHeadDiv">
            <form name="mx_filterselect_hidden_form">
                <table>
                    <tr>
                        <td class="page-title">
                            <h2 id="ph"><%=XSSUtil.encodeForHTML( context, pageHeading )%>
                            </h2>
                        </td>
                    </tr>
                </table>
            </form>
        </div>
    </emxUtil:ifExpr>
    <div id="divPageBody" style="top:<%=XSSUtil.encodeForHTMLAttribute( context, topPixel ) %>;overflow: hidden !important;">
        <iframe id="filterFrame" name="filterFrame" onload="loadComplete()"
                style="height:0px; min-height: 0px; border:0;border-bottom:2px solid #D8D8D8;"
                src="<%=filterFramePage %>" scrolling="no"></iframe>
        <div id="divFilterDivision" class="schClose" style="height:20px; display:none;">
            <button type="button" id="btnSearch"></button>
        </div>
        <iframe id="tableFrame" name="tableFrame" style="top:0px; min-height:0px; height:100%; border:0;"
                src=""></iframe>
        <iframe class='hidden-frame' name='pagehidden' style="height:0px; width:0px;"></iframe>
    </div>
</body>
</html>
<script>
    function loadComplete() {
        var objHeaders = $("#filterFrame").contents().find("td.label,td.labelRequired,td.createLabel,td.createLabelRequired")
        $(objHeaders).each(function (e)
        {
            var obj = $(objHeaders).eq(e);
            var strTitle = $(obj).text();
            $(obj).attr("title", strTitle);
        });
    }
</script>
<%
    }
    catch (Exception e)
    {
        e.printStackTrace();
    }
%>