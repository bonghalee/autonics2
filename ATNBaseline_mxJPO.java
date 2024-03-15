import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Vector;

import matrix.db.Access;
import matrix.db.BusinessObject;
import matrix.db.BusinessObjectList;
import matrix.db.Context;
import matrix.db.JPO;
import matrix.util.MatrixException;
import matrix.util.Pattern;
import matrix.util.StringList;

import com.atn.apps.common.util.ATNConstants;
import com.atn.apps.common.util.ATNDomainUtil;
import com.atn.apps.common.util.ATNSendMailUtil;
import com.atn.apps.pms.util.ATNPMSPropUtil;
import com.atn.apps.pms.util.ATNPMSStringUtil;
import com.matrixone.apps.common.CommonDocument;
import com.matrixone.apps.common.MemberRelationship;
import com.matrixone.apps.common.Route;
import com.matrixone.apps.common.SubtaskRelationship;
import com.matrixone.apps.domain.DomainConstants;
import com.matrixone.apps.domain.DomainObject;
import com.matrixone.apps.domain.DomainRelationship;
import com.matrixone.apps.domain.util.ContextUtil;
import com.matrixone.apps.domain.util.EnoviaResourceBundle;
import com.matrixone.apps.domain.util.FrameworkException;
import com.matrixone.apps.domain.util.FrameworkUtil;
import com.matrixone.apps.domain.util.MapList;
import com.matrixone.apps.domain.util.MqlUtil;
import com.matrixone.apps.domain.util.PersonUtil;
import com.matrixone.apps.domain.util.PropertyUtil;
import com.matrixone.apps.domain.util.eMatrixDateFormat;
import com.matrixone.apps.domain.util.i18nNow;
import com.matrixone.apps.framework.ui.UIUtil;
import com.matrixone.apps.program.ProgramCentralConstants;
import com.matrixone.apps.program.ProgramCentralUtil;
import com.matrixone.apps.program.ProjectSpace;
import com.matrixone.apps.program.Task;

/**
 *<PRE>
 * ATN Standard Deliverable
 * </PRE>

 * @author jkpark
 * @version   1.0 11/13/2015
 */
public class ATNBaseline_mxJPO {

    private static final String ATTRIBUTE_ATNWORKBASELINENUM				= PropertyUtil.getSchemaProperty("attribute_ATNWorkBaselineNum");
    private static final String ATTRIBUTE_ATNBEFORBASELINETASKFLAG			= PropertyUtil.getSchemaProperty("attribute_ATNBeforeBaselineTaskFlag");

    private static final String SELECT_ATTRIBUTE_ATNWORKBASELINENUM			= "attribute[" + ATTRIBUTE_ATNWORKBASELINENUM + "]";
    private static final String SELECT_ATTRIBUTE_ATNBEFORBASELINETASKFLAG	= "attribute[" + ATTRIBUTE_ATNBEFORBASELINETASKFLAG + "]";


    /**
     * Constructor
     *
     * @param context
     * @param args
     * @throws Exception
     */
    public ATNBaseline_mxJPO(Context context, String[] args) throws Exception {
        // do nothing

    }

    /**
     * 일반적인 Property File을 로드한다.
     * @param sFileName String
     * @throws IOException
     * @throws ClassNotFoundException
     * @return Properties
     */
    private static Properties getPropFile( String sFileName ) {
        Properties prop = new Properties();

        try{

            ATNPMSPropUtil pu = new ATNPMSPropUtil();
            InputStream ips = pu.getClass().getResourceAsStream( "/" + sFileName );
            prop.load( ips );
            ips.close();
        }catch(Exception e){

        }
        return prop;
    }

    /**
     * 특정 Properties 파일에서 특정 Attribute의 값을 읽어서 리턴한다.
     * @param sFileName String
     * @param sName String
     * @return String
     */
    private static String getPropValue( String sFileName, String sName ) {
        String sRet = "";
        try {
            sRet = ( String )getPropFile( sFileName ).get( sName );
        } catch( Exception e ) {
        }
        return ATNPMSStringUtil.setEmptyExt( sRet );
    }
    // load properties

    private static final String PLM_URL = getPropValue("Mail.properties","ATN_PLM_URL"); // http://plm.autonics.com:8082

    /**
     * 프로젝트 시작 승인시 베이스라인 최초 버전 자동생성
     * @param context the eMatrix <code>Context</code> object
     * @param args holds the following input arguments:
     *        0 - String containing the object id
     * @throws Exception if operation fails
     * @since V6R2013X.
     */
    public void triggerActionInitATNBaseline(Context context, String[] args) throws Exception {

        String objectId = args[0];

        String InitDescription = "Inital Version";
        HashMap InitAttrMap = new HashMap();


        // find baseline
        MapList list = getBaseline( context , objectId );
        // init baseline
        createBaseline( context , objectId , InitAttrMap , InitDescription , list );
    }

    /**
     * 베이스라인 리스트 조회
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public MapList getATNBaseline(Context context , String[] args) throws Exception{
        Map programMap = (Map)JPO.unpackArgs(args);

        String projectId = (String)programMap.get("objectId");
        return getBaseline( context , projectId );
    }

    private MapList getBaseline( Context context , String objectId ) throws Exception {
        return getBaseline( context, objectId , "" );
    }

    /**
     * 베이스라인 리스트 조회
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    private MapList getBaseline( Context context , String objectId , String objectWhere ) throws Exception{
        MapList list = new MapList();

        DomainObject project = new DomainObject(objectId);
        String type = project.getInfo(context, ProgramCentralConstants.SELECT_TYPE);

        // Type이 BaselineLog이면 연결된 프로젝트의 Id를 입력
        if(ATNConstants.TYPE_ATNBASELINELOG.equals(type))
        {
            objectId = project.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_BASELINE_LOG + "].from.id" );
            project.setId(objectId);
        }

        StringList typeSelects = new StringList();
        StringList relSelects = new StringList();

        typeSelects.add(ProgramCentralConstants.SELECT_ID);
        typeSelects.add(ProgramCentralConstants.SELECT_CURRENT);
        typeSelects.add(ProgramCentralConstants.SELECT_ORIGINATED);
        typeSelects.add(ProgramCentralConstants.SELECT_DESCRIPTION);
        typeSelects.add("attribute["+ATNConstants.ATTRIBUTE_ATNPHASETYPE+"]");
        //typeSelects.add("attribute["+ATNConstants.ATTRIBUTE_ATNNG+"]");
        typeSelects.add("attribute["+ATNConstants.ATTRIBUTE_ATNBASELINENUM+"]");

        relSelects.add(ProgramCentralConstants.SELECT_RELATIONSHIP_ID);



        list = project.getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_BASELINE_LOG,
                "*",
                typeSelects,
                relSelects,
                false,
                true,
                (short)1,
                objectWhere,
                null,
                null,
                null,
                null);
        list.sort(ProgramCentralConstants.SELECT_ORIGINATED, "ascending", "date");
        return list;
    }

    /**
     * 베이스라인 생성하고 연결처리
     * @param context
     * @param projectId
     * @param attrMap
     * @return
     * @throws Exception
     */
    private DomainObject createBaseline( Context context , String projectId , Map attrMap , String description , MapList list ) throws Exception{

        DomainObject project = new DomainObject(projectId);

        DomainObject obj = DomainObject.newInstance(context);

        Locale language	= context.getLocale();

        // 기존 베이스라인이 없을때 새로 생성
        if(list.size() == 0 ){


            String name = obj.getUniqueName();
            String rev  = "-";

            String autoName = DomainObject.getAutoGeneratedName(context, "type_ATNBaselineLog", "");

            String strParentProjectname = project.getInfo(context, ATNConstants.SELECT_ATTRIBUTE_ATNPROJECTTITLE);
            String strBaselineNumValue	= "1";
            String strBaseline			= EnoviaResourceBundle.getProperty(context, "emxFrameworkStringResource", language, "emxFramework.ATNBaselineLog.ScheduleChange");
            String strBaselineNum		= EnoviaResourceBundle.getProperty(context, "emxFrameworkStringResource", language, "emxFramework.Attribute.ATNBaselineNum");
            String strBaselineTitle 	= strParentProjectname + ATNConstants.SYMB_UNDER_BAR + strBaseline + ATNConstants.SYMB_UNDER_BAR + strBaselineNumValue + strBaselineNum;
            attrMap.put(ATNConstants.ATTRIBUTE_TITLE, strBaselineTitle);

            obj.createObject(context, ATNConstants.TYPE_ATNBASELINELOG, autoName, rev, ATNConstants.POLICY_ATNBASELINELOG, ATNConstants.VAULT_ESERVICE_PRODUCTION);
            obj.setAttributeValues(context, attrMap);
            obj.setDescription(context, description);

            // if not connected , then connect
            if( !ATNDomainUtil.isAlreadyConnected(context, project, obj, ATNConstants.RELATIONSHIP_BASELINE_LOG)){

                DomainRelationship.connect(context, project, ATNConstants.RELATIONSHIP_BASELINE_LOG, obj);
            }

            // 베이스라인 완료 상태로 변경
            obj.setState(context, ATNConstants.STATE_ATNBASELINELOG_COMPLETE);

        }else{
            String baselineId = (String)((Map)list.get(0)).get(DomainConstants.SELECT_ID);
            obj.setId(baselineId);
        }

        // 베이스라인 일자 업데이트
        seProjectBaseLineDate( context , projectId , obj );
        // 프로젝트의 Baseline 강제 Setting
        setProjectBaseLine( context , projectId , obj );
        return obj ;

    }
    private void setProjectBaseLine(Context context, String projectId , DomainObject baseline )throws FrameworkException
    {
        try
        {
            com.matrixone.apps.program.ProjectSpace project =
                    (com.matrixone.apps.program.ProjectSpace) DomainObject.newInstance(context,
                            DomainConstants.TYPE_PROJECT_SPACE, "PROGRAM");

            project.setId(projectId);
            // 프로젝트 속성 반영
            String str1 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_START_DATE);
            String str2 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_FINISH_DATE);
//	      String str3 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_START_DATE);
//	      String str4 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_END_DATE);
            String str3 = null;
            String str4 = null;
            // 시작일
            setBaseLineDates(context, project, str1, str3, ProgramCentralConstants.ATTRIBUTE_BASELINE_CURRENT_START_DATE, ProgramCentralConstants.ATTRIBUTE_BASELINE_INITIAL_START_DATE);

            // 종료일
            setBaseLineDates(context, project, str2, str4, ProgramCentralConstants.ATTRIBUTE_BASELINE_CURRENT_END_DATE, ProgramCentralConstants.ATTRIBUTE_BASELINE_INITIAL_END_DATE);
        }
        catch (Exception localException)
        {
            //ContextUtil.abortTransaction(paramContext);
            throw new FrameworkException(localException);
        }
    }
    /**
     * 프로젝트 하위 베이스라인 일자  반영한다.
     * @param context
     * @param projectId
     * @throws FrameworkException
     */
    public void seProjectBaseLineDate(Context context, String projectId , DomainObject baseline ) throws FrameworkException
    {
        try
        {
            //ContextUtil.startTransaction(paramContext, true);
            com.matrixone.apps.program.ProjectSpace project =
                    (com.matrixone.apps.program.ProjectSpace) DomainObject.newInstance(context,
                            DomainConstants.TYPE_PROJECT_SPACE, "PROGRAM");

            project.setId(projectId);

            MapList localMapList = new MapList();

            com.matrixone.apps.common.Task localTask1 = (com.matrixone.apps.common.Task)com.matrixone.apps.common.Task.newInstance(context, ProgramCentralConstants.TYPE_TASK, "Program");

            StringList localStringList = new StringList(10);
            localStringList.add("id");
            localStringList.add(com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_START_DATE);
            localStringList.add(com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_FINISH_DATE);
            localStringList.add(com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_START_DATE);
            localStringList.add(com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_END_DATE);
            localStringList.add(com.matrixone.apps.common.Task.SELECT_BASELINE_CURRENT_START_DATE);
            localStringList.add(com.matrixone.apps.common.Task.SELECT_BASELINE_CURRENT_END_DATE);
            localMapList = com.matrixone.apps.common.Task.getTasks(context, project, 0, localStringList, null, false, false);

            ListIterator localListIterator = localMapList.listIterator();

            // WBS 오브젝트 속성반영 , relationship connect
            while (localListIterator.hasNext())
            {
                Map localObject1 = (Map)localListIterator.next();
                Map localObject2 = new HashMap();
                String str1 = (String)((Map)localObject1).get("id");
                localTask1.setId(str1);

                String str2 = (String)((Map)localObject1).get(com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_START_DATE);
                String str3 = (String)((Map)localObject1).get(com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_FINISH_DATE);
                String str4 = (String)((Map)localObject1).get(com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_START_DATE);
                String str5 = (String)((Map)localObject1).get(com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_END_DATE);

                //2022-11-08
                setProjectLeadModifyAccess(context, projectId, str1);

                // 시작일
                setBaseLineDates(context, localTask1, str2, str4, ProgramCentralConstants.ATTRIBUTE_BASELINE_CURRENT_START_DATE, ProgramCentralConstants.ATTRIBUTE_BASELINE_INITIAL_START_DATE);

                // 종료일
                setBaseLineDates(context, localTask1, str3, str5, ProgramCentralConstants.ATTRIBUTE_BASELINE_CURRENT_END_DATE, ProgramCentralConstants.ATTRIBUTE_BASELINE_INITIAL_END_DATE);

                // if not connected then connect ( baseline -> task )
                // 최초 베이스라인 생성시 타스크와 베이스라인 1차 연결 필요
                // 그러나 2차부터는 wbs 생성후 연결하므로 할 필요없다.
                String hasBaseline = localTask1.getInfo(context, "to["+ATNConstants.RELATIONSHIP_ATNBASELINETASK+"]") ;
                //System.out.println(hasBaseline);
                if( hasBaseline!=null && "FALSE".equalsIgnoreCase(hasBaseline) && !ATNDomainUtil.isAlreadyConnected(context, baseline, localTask1, ATNConstants.RELATIONSHIP_ATNBASELINETASK)){
                    //System.out.println("connect>>>>>");
                    DomainRelationship.connect(context, baseline, ATNConstants.RELATIONSHIP_ATNBASELINETASK, localTask1);
                }
            }

            Object localObject1 = new HashMap();

            // 프로젝트 속성 반영
            String str1 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_START_DATE);
            String str2 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_TASK_ESTIMATED_FINISH_DATE);
            String str3 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_START_DATE);
            String str4 = project.getInfo(context, com.matrixone.apps.common.Task.SELECT_BASELINE_INITIAL_END_DATE);

            // 시작일
            setBaseLineDates(context, project, str1, str3, ProgramCentralConstants.ATTRIBUTE_BASELINE_CURRENT_START_DATE, ProgramCentralConstants.ATTRIBUTE_BASELINE_INITIAL_START_DATE);

            // 종료일
            setBaseLineDates(context, project, str2, str4, ProgramCentralConstants.ATTRIBUTE_BASELINE_CURRENT_END_DATE, ProgramCentralConstants.ATTRIBUTE_BASELINE_INITIAL_END_DATE);

            //ContextUtil.commitTransaction(paramContext);
        } catch (Exception localException) {
            //ContextUtil.abortTransaction(paramContext);
            throw new FrameworkException(localException);
        }
    }

    /**
     *
     * @param context
     * @param object
     * @param estimatedDateVal
     * @param initDateVal
     * @param curBaselineAttrName
     * @param initBaselineAttrName
     * @throws Exception
     */
    private void setBaseLineDates(Context context, DomainObject object, String estimatedDateVal, String initDateVal, String curBaselineAttrName, String initBaselineAttrName)
            throws Exception
    {
        try
        {
            HashMap attrMap = new HashMap();
            // 최초 값이 없을때 계획일자를 업데이트한다.
            if ((null == initDateVal) || ("".equals(initDateVal))) {
                // 최초 베이스라인과 현재 베이스라인 업데이트
                attrMap.put(initBaselineAttrName, estimatedDateVal);
                attrMap.put(curBaselineAttrName, estimatedDateVal);
            }
            else {
                // 현재 베이스라인만 업데이트
                attrMap.put(curBaselineAttrName, estimatedDateVal);
            }

            MqlUtil.mqlCommand(context, "trigger off", true, false);
            object.setAttributeValues(context, attrMap);
            MqlUtil.mqlCommand(context, "trigger on", true, false);
        }
        catch (Exception localException)
        {
            throw new FrameworkException(localException);
        }
    }

    /**
     * 베이스라인 차수
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public Vector getBaseLineNumHTML( Context context , String[] args ) throws Exception
    {
        Vector vRet = new Vector();
        Map programMap =(Map)JPO.unpackArgs(args);
        HashMap paramList = (HashMap) programMap.get("paramList");
        String exportFormat = (String)paramList.get("exportFormat");
        try
        {
            MapList objectList = (MapList) programMap.get("objectList");
            for( int i = 0 ; i < objectList.size() ; i++ ){
                Map mapTask = (Map)objectList.get(i) ;
                String taskId = (String)mapTask.get(CommonDocument.SELECT_ID);
                vRet.add(getCurrentBaselineNum(context,taskId,exportFormat));
            }

        }
        catch (Exception ex)
        {
            ex.printStackTrace();
            throw ex;
        }

        return vRet ;
    }
    /**
     * 베이스라인 차수 컬럼
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public String getCurrentBaselineNum( Context context , String[] args)throws Exception{
        Map programMap = (Map)JPO.unpackArgs(args);
        String objectId = (String)programMap.get("objectId");
        return getCurrentBaselineNum(context,objectId,"");
    }
    /**
     * 베이스라인 차수 컬럼
     * @param context
     * @param taskId
     * @return
     * @throws Exception
     */
    private String getCurrentBaselineNum( Context context , String taskId ,String exportFormat) throws Exception {
        // 익스포트 처리
        boolean isExport = false;
        if("CSV".equals(exportFormat) || "HTML".equals(exportFormat)){
            isExport = true ;
        }
        //String taskId = (String)mapTask.get(CommonDocument.SELECT_ID);
        DomainObject taskObject = DomainObject.newInstance(context, taskId);

        StringList typeSelects = new StringList(3);
        typeSelects.add(CommonDocument.SELECT_ID);
        typeSelects.add("attribute["+ATNConstants.ATTRIBUTE_ATNBASELINENUM+"]");
        typeSelects.add("attribute["+ATNConstants.ATTRIBUTE_ATNPHASETYPE+"]");
        //typeSelects.add(CommonDocument.SELECT_DESCRIPTION);

        StringList relSelects = new StringList(1);
        relSelects.add(CommonDocument.SELECT_RELATIONSHIP_ID);

        String objectWhere = "";

        String relPattern = ATNConstants.RELATIONSHIP_ATNBASELINETASK ;

        MapList baselist = taskObject.getRelatedObjects(context,
                relPattern,
                "*",
                typeSelects,
                relSelects,
                true,
                false,
                (short)1,
                objectWhere,
                null,
                null,
                null,
                null);

        //System.out.println(" baselist size >> "+baselist.size());

        StringBuffer sb = new StringBuffer();

        if(baselist.size()>0){


            for( int j = 0 ; j < baselist.size() ; j ++ ){

                Map map = (Map)baselist.get(j);

                //String devType  = (String)map.get(CommonDocument.SELECT_TYPE);
                String num    = (String)map.get("attribute["+ATNConstants.ATTRIBUTE_ATNBASELINENUM+"]");
                String phase  = (String)map.get("attribute["+ATNConstants.ATTRIBUTE_ATNPHASETYPE+"]");
                //String desc     = (String)map.get(CommonDocument.SELECT_DESCRIPTION);

                String value = num ;
                if( !phase.equals("")) value = value + "("+phase+")";
                boolean isNG = false ;
                if(!num.equals("1")) isNG = true ;


                if(isNG && !isExport ){
                    sb.append("<b><font color='red'>");
                }
                sb.append(value);
                if(isNG && !isExport ){
                    sb.append("</font></b>");
                }

            }
        }else{
            sb.append("1");
        }

        return sb.toString();
    }

    /**
     * 다음번 차수 계산하기
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public String getNextBaselineNum( Context context , String[] args )throws Exception
    {
        // 페이즈 타입이 설정되지 않은 최초의 값이 1이므로 항상 다음번값은 2를 갖는다 .
        String nextNum = "2";

        Map programMap = (Map)JPO.unpackArgs(args);
        String projectId = (String)programMap.get("objectId");
        //String phaseType = (String)programMap.get(ATNConstants.ATTRIBUTE_ATNPHASETYPE);

        //String objectWhere = "";
        //if(phaseType!=null && !"".equals(phaseType)) objectWhere = " attribute["+ATNConstants.ATTRIBUTE_ATNPHASETYPE+"] == '"+phaseType+"' ";

        //MapList list = getBaseline( context , projectId , objectWhere );
        MapList list = getBaseline(context, projectId);
        //System.out.println(objectWhere);
        //System.out.println(list.size());

        if(list.size() > 1)
        {
            // 특정페이즈에 대한 최초의 베이스라인 변경 버전은 2부터 시작하므로 사이즈에 2를 더한다.
            nextNum = new Integer(list.size() + 1).toString();
        }

        return nextNum ;
    }

    /**
     * 베이스라인 변경
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public Map reviseATNBaseline( Context context , String[] args) throws Exception {
        Map resultMap = new HashMap();
        String errorMsg = "";

        try{
            ContextUtil.startTransaction(context, true);

            String languageStr			= context.getSession().getLanguage();
            Locale language				= context.getLocale();
            Map programMap				= (Map)JPO.unpackArgs(args);
            String topId				= (String)programMap.get("topId");
            String[] ATNPhaseType		= (String[])programMap.get("ATNPhaseType");
            String ATNBaselineNum		= (String)programMap.get("ATNBaselineNum");
            String BaselineDescription	= (String)programMap.get("BaselineDescription");
            String[] selectedIds		= (String[])programMap.get("selectedIds");

            StringBuffer sbErrorMsg	= new StringBuffer();
              StringList slTask		= new StringList();
              String[] objectIds		= new String[selectedIds.length];

            for (int i = 0; i < selectedIds.length; i ++){
                String objId = selectedIds[i];
                if(objId.indexOf("|") != -1 ){
                     objId = objId.substring(objId.indexOf("|") + 1);
                }
                objectIds[i] = objId;

                DomainObject dmoTask   = new DomainObject(objectIds[i]);
                 StringList slBusSelect = new StringList();
                slBusSelect.addElement(DomainConstants.SELECT_ID);
                slBusSelect.addElement(DomainConstants.SELECT_NAME);

                //2022-01-18 mod by BoRa,Kim
                StringBuffer sbWhere = new StringBuffer();
                sbWhere.append(ATNConstants.SELECT_CURRENT).append(" == '").append(Route.STATE_ROUTE_IN_PROCESS).append("'");

                MapList mlSubTask = dmoTask.getRelatedObjects(context
                        ,ProgramCentralConstants.RELATIONSHIP_SUBTASK
                        ,ProgramCentralConstants.TYPE_TASK
                        ,slBusSelect
                        ,null
                        ,false
                        ,true
                        ,(short) 0
                        ,null
                        ,null
                        ,0);

                mlSubTask.sort(ATNConstants.SELECT_NAME, "ascending", "string");

                if(mlSubTask.size() > 0) {
                    String strSubTaskId   = "";
                    String strSubTaskName = "";

                    for(int j = 0; j < mlSubTask.size(); j ++) {
                        Map mSubTask   = (Map) mlSubTask.get(j);
                        strSubTaskId   = (String) mSubTask.get(ProgramCentralConstants.SELECT_ID);
                        strSubTaskName = (String) mSubTask.get(ProgramCentralConstants.SELECT_NAME);

                        DomainObject dmoTaskRoute = new DomainObject(strSubTaskId);

                        MapList mlRelRoute = dmoTaskRoute.getRelatedObjects(context
                                ,ProgramCentralConstants.RELATIONSHIP_OBJECT_ROUTE
                                ,ProgramCentralConstants.TYPE_ROUTE
                                ,slBusSelect
                                ,null
                                ,false
                                ,true
                                ,(short) 0
                                ,sbWhere.toString()
                                ,null
                                ,0);

                        //Baseline변경 시 결재중인 Task가 있으면 error
                        if(mlRelRoute.size()>0) {
                            slTask.add(strSubTaskName);
                        }
                    }
                }
            }

            if(!slTask.isEmpty()) {
                sbErrorMsg.append(ProgramCentralUtil.getPMCI18nString(context, "emxProgramCentral.Baseline.ReviseError2", languageStr));

                for(int iTask = 0; iTask < slTask.size(); iTask ++) {
                    sbErrorMsg.append("\n");
                    sbErrorMsg.append(slTask.get(iTask));
                }

                errorMsg = sbErrorMsg.toString();
            }

            if(UIUtil.isNullOrEmpty(errorMsg)) {
                DomainObject project = new DomainObject(topId);

                HashMap attrMap = new HashMap();
                attrMap.put(ATNConstants.ATTRIBUTE_ATNBASELINENUM, ATNBaselineNum);
                attrMap.put(ATNConstants.ATTRIBUTE_ATNPHASETYPE, Arrays.toString(ATNPhaseType).substring(1, Arrays.toString(ATNPhaseType).length()-1));

                String strParentProjectname = project.getInfo(context, ATNConstants.SELECT_ATTRIBUTE_ATNPROJECTTITLE);
                String strBaseline			= EnoviaResourceBundle.getProperty(context, "emxFrameworkStringResource", language, "emxFramework.ATNBaselineLog.ScheduleChange");
                String strBaselineNum		= EnoviaResourceBundle.getProperty(context, "emxFrameworkStringResource", language, "emxFramework.Attribute.ATNBaselineNum");
                attrMap.put(ATNConstants.ATTRIBUTE_TITLE, strParentProjectname+ATNConstants.SYMB_UNDER_BAR+strBaseline+ATNConstants.SYMB_UNDER_BAR+ATNBaselineNum+strBaselineNum);

                // 1. baseline object create
                System.out.println(">>>>>> 1. baseline object createk Start");
                DomainObject baselineObj = DomainObject.newInstance(context);

                // 베이스라인 생성
                String autoName = DomainObject.getAutoGeneratedName(context, "type_ATNBaselineLog", "");

                baselineObj.createObject(context, ATNConstants.TYPE_ATNBASELINELOG, autoName, null, ATNConstants.POLICY_ATNBASELINELOG, ATNConstants.VAULT_ESERVICE_PRODUCTION);
                baselineObj.setAttributeValues(context, attrMap);
                baselineObj.setDescription(context, BaselineDescription);

                // if not connected , then connect
                if( !ATNDomainUtil.isAlreadyConnected(context, project, baselineObj, ATNConstants.RELATIONSHIP_BASELINE_LOG)){

                    // 1.1 connect project - baseline
                    System.out.println(">>>>>> 1.1 connect project - baseline Start");
                    DomainRelationship.connect(context, project, ATNConstants.RELATIONSHIP_BASELINE_LOG, baselineObj);

                    System.out.println(">>>>>> 1.1 connect project - baselinek Finish");

                }
                System.out.println(">>>>>> 1. baseline object create Finish");

                resultMap.put("baselineOID", baselineObj.getObjectId());
                resultMap.put("errorMsg", errorMsg);

                ContextUtil.commitTransaction(context);
            }
        } catch(Exception e) {
            e.printStackTrace();
            errorMsg = e.toString();
            ContextUtil.abortTransaction(context);
        }

        return resultMap ;
    }

    /**
     * 베이스라인 변경으로 WBS 복사
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public String copyWBSByBaseline(Context context, String[] args) throws Exception {
        String errorMsg = "";

        try{
            ContextUtil.startTransaction(context, true);

            Map programMap			= (Map) JPO.unpackArgs(args);
            String topId			= (String) programMap.get("topId");
            String ATNBaselineNum	= (String) programMap.get("ATNBaselineNum");
            String objectId			= (String) programMap.get("baselineOID");
            errorMsg				= (String) programMap.get("errorMsg");
            String[] ATNPhaseType	= (String[]) programMap.get("ATNPhaseType");
            String[] selectedIds	= (String[]) programMap.get("selectedIds");

            if(UIUtil.isNullOrEmpty(errorMsg)) {
                String prevBaselineNum = new Integer( ( new Integer(ATNBaselineNum).intValue() - 1 ) ).toString();
                DomainObject project = new DomainObject(topId);
                DomainObject baselineObj = new DomainObject(objectId);

                // 2. copy wbs
                System.out.println(">>>>>> 2.copy wbs Start");
                _copyAndConnectWBS( context , topId , ATNPhaseType , selectedIds , baselineObj , prevBaselineNum , ATNBaselineNum );
                System.out.println(">>>>>> 2.copy wbs Finish");

                // 3. update baseline dates
                System.out.println(">>>>>> 3. update baseline dates Start");
                seProjectBaseLineDate( context, topId , baselineObj );
                System.out.println(">>>>>> 3. update baseline dates Finish");
                System.out.println("topId >>" + topId + ", objectId >>" + objectId);

                // 4. interface date
                //System.out.println(">>>>>> 4. sendPMSInterface Start");
                //sendPMSInterface( context, topId );
                //System.out.println(">>>>>> 4. sendPMSInterface Finish");

                // 5. send notification
                //System.out.println(">>>>>> 5. send notification Start");
                //sendNotification( context, topId , baselineObj );
                //System.out.println(">>>>>> 5. send notification Finish");

                ContextUtil.commitTransaction(context);
            }
        } catch(Exception e) {
            e.printStackTrace();
            errorMsg = e.toString();
            ContextUtil.abortTransaction(context);
        }

        return errorMsg ;
    }

    /**
     * 베이스라인 변경 1단계 : wbs를 복사해서 추가한다.
     *  복사할 wbs리스크를 특정 단계에 더하고 일자를 반영한다.
     * @param context
     * @param topId 프로젝트 아이디
     * @param ATNPhaseType 추가할 페이즈 유형(ES,PP)
     * @param selectedIds 추가할 wbs
     * @throws Exception
     */
    private MapList _copyAndConnectWBS( Context context , String topId , String[] ATNPhaseType , String[] selectedIds , DomainObject baselineObj ,  String prevBaselineNum , String ATNBaselineNum ) throws Exception
    {
        MapList newWBSList = null;

        for(int iPhase=0; iPhase<ATNPhaseType.length; iPhase++)
        {
            String strPhaseType = ATNPhaseType[iPhase];
            String phaseId = getTargetPhaseId( context , topId , strPhaseType);


            // 1. copy wbs
            System.out.println(">>>>>> 2.1 copy wbs Start");
            com.matrixone.apps.program.ProjectSpace project =
                    (com.matrixone.apps.program.ProjectSpace) DomainObject.newInstance(context,
                            DomainConstants.TYPE_PROJECT_SPACE, DomainConstants.PROGRAM);
            com.matrixone.apps.program.Task phase =
                    (com.matrixone.apps.program.Task) DomainObject.newInstance(context,
                            DomainConstants.TYPE_TASK, DomainConstants.PROGRAM);

            project.setId(topId);
            phase.setId(phaseId);

            // 임시
            String projectName = project.getInfo(context, DomainConstants.SELECT_NAME);
            boolean isOFF = false;
            if(UIUtil.isNotNullAndNotEmpty(projectName) && "DEV200024".equals(projectName)) {
                isOFF = true;
            }

    		/*for(int i = 0; i<selectedIds.length; i++){
				String tempTaskId = selectedIds[i].substring(selectedIds[i].indexOf('|')+1);
	        selectedIds[i] = tempTaskId;
			}*/

            String[] arryTaskId = getPhaseConnectedSelectTasks(context, phase, selectedIds);

            // Phase가 검토 또는 완료 상태이면 진행 상태로 변경
            String strPhaseCurrent = phase.getInfo(context, DomainConstants.SELECT_CURRENT);
            if(ProgramCentralConstants.STATE_PROJECT_TASK_COMPLETE.equals(strPhaseCurrent))
            {
                phase.setState(context, ProgramCentralConstants.STATE_PROJECT_TASK_ACTIVE);
            }

            //task.addTask(context, arryTaskId, null);
            com.matrixone.apps.common.Task task =
                    (com.matrixone.apps.common.Task)DomainObject.newInstance(context, DomainConstants.TYPE_TASK);

            if(isOFF) {
                MqlUtil.mqlCommand(context, "trigger off", true, false);
            }

            for(int i = 0; i < arryTaskId.length; i++)
            {
                System.out.println(" Task Object ID : " + arryTaskId[i]);
                task.setId(arryTaskId[i]);
                //Task task11 = new Task(task);
                //Task taskTargetParent = new Task(phaseId);
                task.cloneTaskWithStructure(context, phase, null, null, false);

                task.setAttributeValue(context, ATTRIBUTE_ATNWORKBASELINENUM, ATNBaselineNum);
                task.setAttributeValue(context, ATTRIBUTE_ATNBEFORBASELINETASKFLAG, "Y");
            }

            if(isOFF) {
                MqlUtil.mqlCommand(context, "trigger on", true, false);
            }

            System.out.println(">>>>>> 2.1 copy wbs Finish");

            MapList oldTasks = getOldWBS( context, phase , prevBaselineNum );
            MapList newTasks = getNewWBS( context, phase );

            StringList slSelectAllTask = getSelectedAllTask(context, arryTaskId);
            for(int i = 0; i < slSelectAllTask.size(); i++)
            {
                DomainObject doOldTask = new DomainObject((String)slSelectAllTask.get(i));
                doOldTask.setAttributeValue(context, ATTRIBUTE_ATNWORKBASELINENUM, ATNBaselineNum);
                doOldTask.setAttributeValue(context, ATTRIBUTE_ATNBEFORBASELINETASKFLAG, "Y");
            }


            // 2. connect to baseline
            System.out.println(">>>>>> 2.2 connect to baseline Start");
            copyBaselineConnection(context, baselineObj, newTasks , oldTasks, slSelectAllTask);
            System.out.println(">>>>>> 2.2 connect to baseline End");
    		/*for( int i = 0 ; i < newTasks.size(); i++ ){
				String newId = (String)((Map)newTasks.get(i)).get(DomainConstants.SELECT_ID);
				DomainObject taskObj = new DomainObject(newId);
				//이전 Task의 리비전 정보 가져오기
				String preRevision = (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_REVISION);

				// if not connected , then connect
				if( !ATNDomainUtil.isAlreadyConnected(context, baselineObj, taskObj, ATNConstants.RELATIONSHIP_ATNBASELINETASK)){
					DomainRelationship dmoRel = DomainRelationship.connect(context, baselineObj, ATNConstants.RELATIONSHIP_ATNBASELINETASK, taskObj);
					//테스트
					dmoRel.setAttributeValue(context, ATNConstants.ATTRIBUTE_ATNPREVIOUSTASKREVISION, preRevision);
				}
			}*/


            // connect to dependency
            copyDependencyNewTasks(context, baselineObj, newTasks , oldTasks, slSelectAllTask);


            System.out.println(">>>>>> 2.1.1 set date new wbs Start");
            Date today = new Date();
            String strToday = new java.text.SimpleDateFormat("MM/dd/yyyy").format(today) + " 8:00:00 AM";

            emxTask_mxJPO taskJPO = new emxTask_mxJPO(context,null);

            for( int i = 0 ; i < newTasks.size() ; i++ ){
                String newId = (String)((Map)newTasks.get(i)).get("id");
    			/*DomainObject newTask = new DomainObject(newId);
				newTask.setAttributeValue(context, "Task Constraint Type", "Must Start On");
				newTask.setAttributeValue(context, "Task Estimated Start Date", strToday ); // 오늘부터 계획일 설정
    			 */

                // Update Start Date 시작
                //double clientTZOffset 	= Double.parseDouble((String)(requestMap.get("timeZone")));
                //int iDateFormat 		= eMatrixDateFormat.getEMatrixDisplayDateFormat();
                //DateFormat format 		= DateFormat.getDateTimeInstance(iDateFormat, iDateFormat, locale);

                //String fieldValue 		= eMatrixDateFormat.getFormattedInputDate(context,newAttrValue, clientTZOffset,locale);
                //String tempfieldValue = eMatrixDateFormat.getFormattedDisplayDateTime(context, fieldValue, true,iDateFormat, clientTZOffset,locale);
                Date dateTemp 				= new java.text.SimpleDateFormat(eMatrixDateFormat.strEMatrixDateFormat, Locale.US).parse(strToday);

                taskJPO.updateEstimatedDate(context,newId,"startDate",dateTemp,topId,null);

                com.matrixone.apps.program.Task taskTemp =
                        (com.matrixone.apps.program.Task) DomainObject.newInstance(context,
                                DomainConstants.TYPE_TASK, DomainConstants.PROGRAM);
                taskTemp.setId(newId);
                //taskTemp.setAttributeValue(context, "Task Constraint Type", "Must Start On");
                taskTemp.setAttributeValue(context, ProgramCentralConstants.ATTRIBUTE_TASK_CONSTRAINT_TYPE, ProgramCentralConstants.ATTRIBUTE_TASK_CONSTRAINT_TYPE_RANGE_ASAP);
                taskTemp.setAttributeValue(context, ATTRIBUTE_ATNWORKBASELINENUM, "0");
                taskTemp.setAttributeValue(context, ATTRIBUTE_ATNBEFORBASELINETASKFLAG, "N");
                //taskTemp.setAttributeValue(context, ATNConstants.ATTRIBUTE_ATNCHECKTEMPLATEYN, "N");
                taskTemp.rollupAndSave(context);

                // Update Start Date 종료
            }
            System.out.println(">>>>>> 2.1.1 set date new wbs Finish");


            // 3. promote to assign
            System.out.println(">>>>>> 2.3 promote to assign Start");
            for( int i = 0 ; i < newTasks.size(); i++ ){
                String newId = (String)((Map)newTasks.get(i)).get(DomainConstants.SELECT_ID);

                DomainObject taskObj = new DomainObject(newId);
                String taskCurrent = taskObj.getInfo(context,"current");
                String taskType    = taskObj.getInfo(context,"type");

                // 타입이 Task,Phase일때 상태가 Create일때만 Assign으로 프로모트 처리한다.
                if( ( taskType.equals("Phase") || taskType.equals("Task") )&& taskCurrent.equals(ProgramCentralConstants.STATE_PROJECT_TASK_CREATE)){
                    taskObj.promote(context);
                }
            }
            System.out.println(">>>>>> 2.3 promote to assign End");

            // 4. connect assignee
            System.out.println(">>>>>> 2.4 connect assignee Start");
            for( int i = 0 ; i < newTasks.size(); i++ ){
                // 담당자를 복사한다.
                copyAssigneeConnection( context, (Map)newTasks.get(i) , oldTasks , slSelectAllTask ) ;
            }
            System.out.println(">>>>>> 2.4 connect assignee End");

            // 5. connect Standard Deliverable
            System.out.println(">>>>>> 2.5 connect Standard Deliverable Start");
            for( int i = 0 ; i < newTasks.size(); i++ ){
                // 표준산출물을 복사한다.
                copyStandardDeliverableConnection(context, (Map)newTasks.get(i), oldTasks, slSelectAllTask);
                // 산출물을 복사한다.
                copyTaskDeliverableConnection(context, (Map)newTasks.get(i), oldTasks, slSelectAllTask);
            }
            System.out.println(">>>>>> 2.5 connect Standard Deliverable End");
        }

        return newWBSList ;
    }

    /**
     * 베이스라인 생성화면에서 선택된 Task들의 하위에 있는 모든 Task Object Id를 반환
     * @param context
     * @param arryTaskId
     * @return slAllTask
     * @throws Exception
     */
    private StringList getSelectedAllTask(Context context, String[] arryTaskId) throws Exception {

        StringList slAllTask = new StringList();
        StringList typeSelects = new StringList();

        typeSelects.add(ProgramCentralConstants.SELECT_ID);

        for(int iArry=0; iArry<arryTaskId.length; iArry++)
        {
            try
            {
                DomainObject dmoTask = new DomainObject(arryTaskId[iArry]);

                MapList mlSubTask = dmoTask.getRelatedObjects(context,
                        ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                        ProgramCentralConstants.TYPE_TASK,
                        typeSelects,
                        null,
                        false,
                        true,
                        (short)0,
                        null,
                        null,
                        0);

                if(mlSubTask.size()>0)
                {
                    for(int iTask=0; iTask<mlSubTask.size(); iTask++)
                    {
                        Map mSubTask = (Map) mlSubTask.get(iTask);
                        String strSubTaskId = (String) mSubTask.get(ProgramCentralConstants.SELECT_ID);

                        slAllTask.addElement(strSubTaskId);
                    }

                    slAllTask.addElement(arryTaskId[iArry]);
                }
                else
                {
                    slAllTask.addElement(arryTaskId[iArry]);
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
        }

        return slAllTask;
    }

    /**
     * 프로젝트내 추가할 페이즈 아이디 리턴
     * @param context
     * @param topId
     * @param ATNPhaseType
     * @return
     * @throws Exception
     */
    private String getTargetPhaseId( Context context, String topId , String ATNPhaseType ) throws Exception{
        String oid = "";

        StringList typeSelects = new StringList(1);
        typeSelects.add(CommonDocument.SELECT_ID);

        StringList relSelects = new StringList(1);
        relSelects.add(CommonDocument.SELECT_RELATIONSHIP_ID);

    	/*String phaseName = "";
    	if(ATNPhaseType.equals("ES")) phaseName = "E/S";
    	if(ATNPhaseType.equals("PP")) phaseName = "P/P";*/
        String objectWhere = "name == \'" + ATNPhaseType + "\'";

        String relPattern = ATNConstants.RELATIONSHIP_ATNBASELINETASK ;

        MapList phaseList = new DomainObject(topId).getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                ProgramCentralConstants.TYPE_PHASE,
                typeSelects,
                relSelects,
                false,
                true,
                (short)1,
                objectWhere,
                null,
                null,
                null,
                null);

        if(phaseList.size()!= 0){
            oid= (String)((Map)phaseList.get(0)).get(ProgramCentralConstants.SELECT_ID);
        }
        return oid ;
    }


    /**
     * 페이즈내 추가된 타스크 리스트
     * @param context
     * @param phase
     * @return
     * @throws Exception
     */
    private MapList getNewWBS( Context context, DomainObject phase  ) throws Exception{
        //String oid = "";

        StringList typeSelects = new StringList(4);
        typeSelects.add(CommonDocument.SELECT_ID);
        typeSelects.add(CommonDocument.SELECT_NAME);
        typeSelects.add("attribute[Task Constraint Type]");
        typeSelects.add("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + CommonDocument.SELECT_NAME);

        StringList relSelects = new StringList(2);
        relSelects.add(CommonDocument.SELECT_RELATIONSHIP_ID);
        relSelects.add("attribute[Sequence Order]");

        String objectWhere = " current == '" +ProgramCentralConstants.STATE_PROJECT_TASK_CREATE+"' && to["+ATNConstants.RELATIONSHIP_ATNBASELINETASK+"] == 'False'" ;

        //String relPattern = ATNConstants.RELATIONSHIP_ATNBASELINETASK ;

        MapList wbsList = phase.getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                "*",
                typeSelects,
                relSelects,
                false,
                true,
                (short)0,
                objectWhere,
                null,
                null,
                null,
                null);

        wbsList.sort("attribute[Sequence Order]","asending","integer");

        System.out.println("new task ids>>>"+wbsList);

        return wbsList ;
    }

    /**
     * 페이즈내 기존 타스크 리스트
     * @param context
     * @param phase
     * @param prevBaselineNum
     * @return
     * @throws Exception
     */
    private MapList getOldWBS( Context context, DomainObject phase , String prevBaselineNum ) throws Exception{

        StringList typeSelects = new StringList(4);
        typeSelects.add(CommonDocument.SELECT_ID);
        typeSelects.add(CommonDocument.SELECT_NAME);
        typeSelects.add(CommonDocument.SELECT_REVISION);
        typeSelects.add("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + CommonDocument.SELECT_NAME);

        StringList relSelects = new StringList(1);
        relSelects.add(CommonDocument.SELECT_RELATIONSHIP_ID);

        // 이전 차수 검색
        //String objectWhere = " ( current != '" +ProgramCentralConstants.STATE_PROJECT_TASK_CREATE+"' ) && ( to["+ATNConstants.RELATIONSHIP_ATNBASELINETASK+"].from.attribute["+ATNConstants.ATTRIBUTE_ATNBASELINENUM+"] == '"+prevBaselineNum+"' ) " ;
        // 모든 이전 차수 검색
        String objectWhere = " ( current != '" +ProgramCentralConstants.STATE_PROJECT_TASK_CREATE+"' )  " ;

        //String relPattern = ATNConstants.RELATIONSHIP_ATNBASELINETASK ;

        MapList wbsList = phase.getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                "*",
                typeSelects,
                relSelects,
                false,
                true,
                (short)0,
                objectWhere,
                null,
                null,
                null,
                null);

        return wbsList ;
    }

    /**
     * 기존 타스크의 담당자 연결을 복사하여 연결한다.
     * @param context
     * @param newTaskMap
     * @param oldTasks
     * @throws Exception
     */
    private void copyAssigneeConnection( Context context, Map newTaskMap , MapList oldTasks , StringList slSelectAllTask ) throws Exception
    {
        String newId		 = (String)newTaskMap.get(DomainConstants.SELECT_ID);
        String newName		 = (String)newTaskMap.get(DomainConstants.SELECT_NAME);
        String newParentName = (String) newTaskMap.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

        DomainObject newTask = new DomainObject(newId);

        HashMap attrMap = new HashMap();
        attrMap.put(ProgramCentralConstants.ATTRIBUTE_PERCENT_ALLOCATION, "100.0");
        attrMap.put(ProgramCentralConstants.ATTRIBUTE_ASSIGNEE_ROLE, "Task Assignee");

        for( int i = 0 ; i < oldTasks.size() ; i++  ){

            String taskName 		= (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_NAME);
            String oldId			= (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_ID);
            String oldParentName 	= (String)((Map)oldTasks.get(i)).get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

            if(slSelectAllTask.contains(oldId) && oldParentName.equals(newParentName) && taskName.equals(newName)){

                StringList typeSelects = new StringList(2);
                typeSelects.add(CommonDocument.SELECT_ID);
                typeSelects.add(CommonDocument.SELECT_NAME);

                StringList relSelects = new StringList(1);
                relSelects.add(CommonDocument.SELECT_RELATIONSHIP_ID);

                MapList assigneeList = new DomainObject(oldId).getRelatedObjects(context,
                        ProgramCentralConstants.RELATIONSHIP_ASSIGNED_TASKS,
                        ProgramCentralConstants.TYPE_PERSON,
                        typeSelects,
                        relSelects,
                        true,
                        false,
                        (short)1,
                        "",
                        null,
                        null,
                        null,
                        null);

                for( int j = 0 ; j < assigneeList.size() ; j++ ){

                    DomainObject person = new DomainObject( (String)((Map)assigneeList.get(j)).get(DomainConstants.SELECT_ID));
                    // if not connected , then connect
                    if( !ATNDomainUtil.isAlreadyConnected(context, person, newTask, ProgramCentralConstants.RELATIONSHIP_ASSIGNED_TASKS)){
                        DomainRelationship rel = DomainRelationship.connect(context, person, ProgramCentralConstants.RELATIONSHIP_ASSIGNED_TASKS, newTask );
                        rel.setAttributeValues(context, attrMap);
                    }
                }
                // 최초 타스크명이 매칭되는 항목만 적용하기 위해 브레이크 처리한다.
                break;


            }

        }

    }


    /**
     * 기존 타스크의 표준산출물 연결을 복사하여 연결한다.
     * @param context
     * @param newTaskMap
     * @param oldTasks
     * @throws Exception
     */
    private void copyStandardDeliverableConnection( Context context, Map newTaskMap , MapList oldTasks , StringList slSelectAllTask ) throws Exception {
        String newId			= (String)newTaskMap.get(DomainConstants.SELECT_ID);
        String newName			= (String)newTaskMap.get(DomainConstants.SELECT_NAME);
        String newParentName	= (String) newTaskMap.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

        StringList typeSelects = new StringList(2);
        typeSelects.add(ATNConstants.SELECT_ID);
        typeSelects.add(ATNConstants.SELECT_NAME);

        StringList relSelects = new StringList(2);
        relSelects.add(ATNConstants.SELECT_RELATIONSHIP_ID);
        relSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNSTDREQUIRED);

        DomainObject newTask	= new DomainObject(newId);
        DomainObject oldTask	= new DomainObject();
        DomainObject stdObj		= new DomainObject();

        for(int i=0; i<oldTasks.size(); i++) {
            String taskName			= (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_NAME);
            String oldId 			= (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_ID);
            String odlParentName	= (String)((Map)oldTasks.get(i)).get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

            if(slSelectAllTask.contains(oldId) && odlParentName.equals(newParentName) && taskName.equals(newName)) {
                oldTask.setId(oldId);

                MapList stdDelList = oldTask.getRelatedObjects(context, ATNConstants.RELATIONSHIP_ATNSTANDARDDELIVERABLE, ATNConstants.SYMB_WILD,
                        typeSelects, relSelects, false, true, (short) 1, ATNConstants.EMPTY_STRING, ATNConstants.EMPTY_STRING, 0);

                for(int j=0; j<stdDelList.size(); j++) {
                    Map stdDelMap		= (Map) stdDelList.get(j);
                    String sStdDelId	= (String) stdDelMap.get(ATNConstants.SELECT_ID);
                    String sStdRequired	= (String) stdDelMap.get(ATNConstants.SELECT_ATTRIBUTE_ATNSTDREQUIRED);

                    stdObj.setId(sStdDelId);

                    if( !ATNDomainUtil.isAlreadyConnected(context, newTask, stdObj, ATNConstants.RELATIONSHIP_ATNSTANDARDDELIVERABLE)){
                        DomainRelationship rel = DomainRelationship.connect(context, newTask, ATNConstants.RELATIONSHIP_ATNSTANDARDDELIVERABLE, stdObj);
                        rel.setAttributeValue(context, ATNConstants.ATTRIBUTE_ATNSTDREQUIRED, sStdRequired);
                    }
                }

                // 최초 타스크명이 매칭되는 항목만 적용하기 위해 브레이크 처리한다.
                break;
            }
        }
    }

    /**
     * editable Table에서 ATNNG 속성 업데이트
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public void updateATNNG_EditableTable(Context context, String[] args)

            throws Exception

    {

        HashMap programMap = (HashMap) JPO.unpackArgs(args);

        HashMap paramMap = (HashMap) programMap.get("paramMap");

        HashMap requestMap = (HashMap) programMap.get("requestMap");

        // Get the required parameter values from "paramMap" - as required

        String objectId = (String) paramMap.get("objectId ");

        String relId = (String) paramMap.get("relId ");

        String newValue = (String) paramMap.get("New Value");

        String oldValue = (String) paramMap.get("Old Value");

        // get languagestr from requestmap

        String languageStr = (String) requestMap.get("languageStr");

        // Define and add selects if required

        // Process the information to set the field values for the current object

        System.out.println("ATNBaseline.updateATNNG>>>objectId="+objectId+",New Value="+newValue);


    }


    /**
     * NG 구분 항목 테이블 출력
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public Vector getATNNGColumn(Context context, String[] args)  throws Exception
    {
        Vector vRet = new Vector();

        // when called in web form range program
        HashMap programMap	= (HashMap) JPO.unpackArgs(args);
        //System.out.println("programMap>>>"+programMap);
        //HashMap is defined to retrieve another HashMap in the unpacked list, that has the object information
        HashMap paramList = (HashMap) programMap.get("paramList");
        //System.out.println("paramList>>>"+paramList);
        //	HashMap requestValuesMap = (HashMap) paramList.get("RequestValuesMap");

        HashMap columnMap = (HashMap) programMap.get("columnMap");
        //System.out.println("columnMap>>>"+columnMap);
        HashMap settingMap = (HashMap) columnMap.get("settings");
        //System.out.println("settingMap>>>"+settingMap);

        MapList objectList = (MapList) programMap.get("objectList");
        //System.out.println("objectList>>>"+objectList);


        String sLanguage = context.getSession().getLanguage();

        String sMode = "view";// (String) requestMap.get("mode");
        /*String editTableMode = (String)paramList.get("editTableMode");
        if( editTableMode!=null && "true".equals(editTableMode)) sMode = "edit";*/


        String sAttName = (String) columnMap.get("name");
        String sCols = (String) settingMap.get("Cols");
        if(sCols==null)
            sCols = "0";


        for( int i = 0 ; i < objectList.size() ; i++ ){

            String objectId = (String)((Map)objectList.get(i)).get("id");
            DomainObject obj = new DomainObject(objectId);

            if(sAttName.contains("attribute_")) sAttName = PropertyUtil.getSchemaProperty(sAttName) ;
            String sAttValue = obj.getInfo(context, "attribute["+sAttName+"]");

            //System.out.println( obj.exists(context)+","+sAttName + ","+sAttValue+","+sMode+","+sLanguage);

            vRet.add(ATNUIUtil_mxJPO.displayCheckbox(context, sAttName, sAttValue, sMode, sLanguage, "", "", Integer.parseInt(sCols)) );
        }

        return vRet ;

    }

    /**
     * Baseline 결재 완료 시 일정변경 알림 메일 발송
     * @param context
     * @param args
     * @return int
     * @throws Exception
     */
    public int triggerSendMailBaselineComplete(Context context, String[] args) throws Exception{
        String baseLineId = args[0];
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy. MM. dd", Locale.US);

        try{
            DomainObject baseline = new DomainObject(baseLineId);

            StringList slSelect = new StringList(3);
            slSelect.add(ATNConstants.SELECT_OWNER);
            slSelect.add(ATNConstants.SELECT_ATTRIBUTE_ATNPHASETYPE);
            slSelect.add(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);

            Map baselineMap		= baseline.getInfo(context, slSelect);
            String owner		= (String) baselineMap.get(ATNConstants.SELECT_OWNER);
            String phaseType	= (String) baselineMap.get(ATNConstants.SELECT_ATTRIBUTE_ATNPHASETYPE);
            String num			= (String) baselineMap.get(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);

            // project OID
            String projectId = baseline.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_BASELINE_LOG + "].from.id");

            DomainObject project = new DomainObject(projectId);
            String prjTitle	= project.getAttributeValue(context, ATNConstants.SELECT_ATTRIBUTE_ATNPROJECTTITLE);

            String strBaselineReviseNotiPrefix =i18nNow.getI18nString("emxFramework.Common.Mail.BaselineReviseNotiTitlePrefix", "emxFrameworkStringResource", context.getSession().getLanguage());
            //String strBaselineReviseNotiDesc   =i18nNow.getI18nString("emxFramework.Common.Mail.Desc.BaselineReviseNotiDesc", "emxFrameworkStringResource", context.getSession().getLanguage());

            // title
            String MailTitle= "PLM_["+strBaselineReviseNotiPrefix+"] "+prjTitle+" " + phaseType + " " + num +"차 ";

            // content
            String content = setMailContent(context,projectId,baseline);

            // from - PM , to - members
            String sFromAddress	= PersonUtil.getEmail(context, owner);
            String[] toAdress	= getProjectMemberEmails(context, projectId);

            ATNSendMailUtil.sendMail_HTMLContent(context, sFromAddress, toAdress, MailTitle, content);
        }catch(Exception e){
            e.printStackTrace();

        }

        return 0;
    }

    /**
     * 일정변경시 해당 프로젝트의 인터페이스 실행 ( 프로젝트 정보 / ES PP 정보 / Event 정보 )
     * @param context
     * @param projectId
     * @throws Exception
     */
    private void sendPMSInterface( Context context , String projectId )throws Exception{
        //ATNPMSInterface_mxJPO jpo = new ATNPMSInterface_mxJPO( context , null );

        HashMap programMap = new HashMap();
        programMap.put("objectId", projectId);

        String[] args = JPO.packArgs(programMap);

        // 1. 프로젝트
        //jpo.ProjectInterface(context, args);System.out.println("PROJECT IF END");

        // 2. ES PP
        //jpo.ESPPEstimatedInterFace(context, args);System.out.println("ESPP IF END");

        // 3. Event
        //jpo.ESPPEventInterFace(context, args);System.out.println("EVENT IF END");

    }

    /**
     * 멤버 메일주소 리턴
     * @param context
     * @param projectId
     * @return
     */
    public String[]  getProjectMemberEmails( Context context , String projectId ) throws Exception {
        DomainObject project = new DomainObject(projectId);

        StringList select = new StringList();
        select.add("attribute[Email Address]");

        MapList list  = project.getRelatedObjects(context,
                "Member",    					 //relationship Name
                "Person", 				 //  Type Name
                select,           				 // object selects  : 최종 개체로부터 추출할 속성값들
                null,     					   	    // relationship selects  : 릴레이션의속성값들
                false,               			    // from   direction
                true,          			        // to direction
                (short) 0,        		        // recursion level  : 관계의 수준
                null,								// object where clause : 최종개체추출의 where절(mql )
                " attribute[Project Role] != 'Project Lead' ") ;              			   // relationship where clause  : 릴레이션 의 where 절
        //System.out.println(list);
        String[] mail = new String[list.size()];
        for( int i = 0 ; i < list.size() ; i++ ){
            mail[i] = (String)((Map)list.get(i)).get("attribute[Email Address]");
        }

        return mail ;

    }

    private static String getPMDept( Context context ) throws Exception {
        //get pmOID businessUnit
        String pmOID	= PersonUtil.getPersonObjectID(context, context.getUser());
        StringList select = new StringList();
        select.add("attribute[Organization Name]");

        DomainObject object = new DomainObject(pmOID);

        MapList firstDep  = object.getRelatedObjects(context,
                "Member",    					 //relationship Name
                "Business Unit", 				 //  Type Name
                select,           				 // object selects  : 최종 개체로부터 추출할 속성값들
                null,     					   	    // relationship selects  : 릴레이션의속성값들
                true,          			        // to direction
                false,               			    // from   direction
                (short) 0,        		        // recursion level  : 관계의 수준
                null,								// object where clause : 최종개체추출의 where절(mql )
                null) ;              			   // relationship where clause  : 릴레이션 의 where 절

        String projDept = (String)((Map)firstDep.get(0)).get("attribute[Organization Name]");
        if(projDept.equals(null)){projDept="";}

        return projDept ;
    }

    /**
     * 일정변경 알림 메일 내용
     * @param context
     * @param projectInfoMap
     * @return
     * @throws Exception
     */
    private static String setMailContent(Context context, String projectId , DomainObject baseline ) throws Exception{
        StringBuffer returnValue = new StringBuffer();

        // delay util
        ATNPMSCommon_mxJPO common = new ATNPMSCommon_mxJPO(context,null);

        String lang = context.getSession().getLanguage();

        String si18nProjectInfo = i18nNow.getI18nString("emxFramework.Common.Mail.ProjectInfo", "emxFrameworkStringResource", lang);
        String si18nBasicInfo   = i18nNow.getI18nString("emxFramework.Common.Mail.BasicInfo", "emxFrameworkStringResource", lang);
        String si18nTaskName    = i18nNow.getI18nString("emxFramework.Common.Mail.TaskName", "emxFrameworkStringResource", lang);
        String si18nBusinessUnit= i18nNow.getI18nString("emxFramework.Common.Mail.BusinessUnit", "emxFrameworkStringResource", lang);
        String si18nType        = i18nNow.getI18nString("emxFramework.Common.Mail.Type", "emxFrameworkStringResource", lang);

        String si18nATNProjectTitle = i18nNow.getI18nString("emxFramework.Attribute.ATNProjectTitle" , "emxFrameworkStringResource" , lang);
        String si18nATNDevelopmentClass = i18nNow.getI18nString("emxFramework.Attribute.ATNDevelopmentClass" , "emxFrameworkStringResource" , lang);
        String si18nATNModelNumber = i18nNow.getI18nString("emxFramework.Common.Mail.ModelName" , "emxFrameworkStringResource" , lang);
        String si18nATNStandard = i18nNow.getI18nString("emxFramework.Attribute.ATNStandard" , "emxFrameworkStringResource" , lang);
        String si18nATNPhaseType = i18nNow.getI18nString("emxFramework.Attribute.ATNPhaseType" , "emxFrameworkStringResource" , lang);
        String si18nATNBaselineNum = i18nNow.getI18nString("emxFramework.Attribute.ATNBaselineNum" , "emxFrameworkStringResource" , lang);

        String si18nBaseDesc = i18nNow.getI18nString("emxFramework.Mail.BaseDesc" , "emxFrameworkStringResource" , lang);
        String si18nBaseInit = i18nNow.getI18nString("emxFramework.Mail.BaseInit" , "emxFrameworkStringResource" , lang);
        String si18nBaseCur = i18nNow.getI18nString("emxFramework.Mail.BaseCur" , "emxFrameworkStringResource" , lang);


        String si18nPM 	        = i18nNow.getI18nString("emxFramework.Common.Mail.PM", "emxFrameworkStringResource", lang);
        String si18nDelay       = i18nNow.getI18nString("emxFramework.Common.Mail.Delay", "emxFrameworkStringResource", lang);
        String si18nDevGrade    = i18nNow.getI18nString("emxFramework.Common.Mail.DevGrade", "emxFrameworkStringResource", lang);
        String si18nModelName   = i18nNow.getI18nString("emxFramework.Common.Mail.ModelName", "emxFrameworkStringResource", lang);
        String si18nStandard    = i18nNow.getI18nString("emxFramework.Common.Mail.Standard", "emxFrameworkStringResource", lang);
        String si18nEstStart    = i18nNow.getI18nString("emxFramework.Common.Mail.EstStart", "emxFrameworkStringResource", lang);
        String si18nEstFinish   = i18nNow.getI18nString("emxFramework.Common.Mail.EstFinish", "emxFrameworkStringResource", lang);
        String si18nActStart    = i18nNow.getI18nString("emxFramework.Common.Mail.ActStart", "emxFrameworkStringResource", lang);
        String si18nActFinish   = i18nNow.getI18nString("emxFramework.Common.Mail.ActFinish", "emxFrameworkStringResource", lang);
        String si18nDeliverable = i18nNow.getI18nString("emxFramework.Common.Mail.Deliverable", "emxFrameworkStringResource", lang);

        String sMessage = i18nNow.getI18nString("emxFramework.Common.Mail.Message", "emxFrameworkStringResource", lang);

        String si18nPJTCODE = "PJTCODE";

        DomainObject project = new DomainObject(projectId);

        Map projMap = project.getAttributeMap(context);
        Map baseMap = baseline.getAttributeMap(context);

        // info str
        String messageDesc   =i18nNow.getI18nString("emxFramework.Common.Mail.BaselineReviseNotiDesc", "emxFrameworkStringResource", lang);

        // PJT CODE
        String sPJTCODE = (String)project.getInfo(context,"name");

        // PM
        String sPMfullName  = com.atn.apps.common.util.ATNPersonUtil.getUserFullName(context, context.getUser());

        // Dept
        String Dept = getPMDept(context);

        // Delay
        String[] delay = common.getProjectDelay(context, projectId);
        String curPhase = delay[0];
        String dlyDay = delay[1];
        String dlyPercent = delay[2];

        // base Desc
        String baseDesc = baseline.getInfo(context,"description");

        baseDesc = convertMultilineStr( baseDesc );

        // baseInitStr
        String baseInitStr = convertDateFormat( context , (String)projMap.get("Baseline Initial Start Date")) +" ~ "+convertDateFormat( context , (String)projMap.get("Baseline Initial End Date")) ;

        String baseCurStr = convertDateFormat( context , (String)projMap.get("Baseline Current Start Date")) +" ~ "+convertDateFormat( context , (String)projMap.get("Baseline Current End Date")) ;


        returnValue.append("<!DOCTYPE HTML PUBLIC \"-//W3C//DTD HTML 4.01 Transitional//EN\">");
        returnValue.append("<html>");
        returnValue.append("<head>");
        returnValue.append("<title>Untitled Document</title>");
        returnValue.append("<meta http-equiv=\"Content-Type\" content=\"text/html; charset=euc-kr\">");
        returnValue.append("<style type=\"text/css\">");
        returnValue.append("<!--");
        returnValue.append("	img {vertical-align:middle; border:0;}");
        returnValue.append("	td {padding-left:10px;}");
        returnValue.append("	table {border:solid 7px #045dc4; font:12px/16px Dotum,\"돋움\",Tahoma,sans-serif; color:#034086;}");
        returnValue.append("-->");
        returnValue.append("</style>");
        returnValue.append("</head>");
        returnValue.append("<body>");
        returnValue.append("<table width=\"500\" height=\"200\" cellpadding=\"0\" cellspacing=\"0\">");
        returnValue.append("  <tr bgcolor=\"045dc4\">");
        returnValue.append("    <td height=\"50\" colspan=\"2\" style=\"padding-left:0px\"; ><img src=\""+PLM_URL+"/3dspace/common/images/mail_01.gif\" width=\"500\" height=\"50\"></td>");
        returnValue.append("  </tr>");
        returnValue.append("  <tr bgcolor=\"d6e4f4\">");
        returnValue.append("    <td height=\"30\" colspan=\"2\" ><img src=\""+PLM_URL+"/3dspace/common/images/mail_02.gif\" width=\"67\" height=\"21\"></td>");
        returnValue.append("  </tr>");
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+sMessage+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+messageDesc +"</td>");
        returnValue.append("  </tr>");
        // project info
        returnValue.append("  <tr bgcolor=\"d6e4f4\">");
        returnValue.append("    <td height=\"40\" colspan=\"2\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nProjectInfo+"</strong></td>");
        returnValue.append("  </tr>");
        // PJT CODE
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nPJTCODE+"</strong></td>");
        returnValue.append("    <td width=\"318\" height=\"40\" style=\"border-bottom:solid 1px #bdbebf;\"><b><a href=\""+PLM_URL+"/3dspace/common/emxNavigator.jsp?objectId=" + projectId + "\" target=\"_blank\">"+sPJTCODE+"</b></a></td>");
        returnValue.append("  </tr>");
        // PJT TITLE
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nATNProjectTitle+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+projMap.get("ATNProjectTitle") +"</td>");
        returnValue.append("  </tr>");
        // DEPT
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nBusinessUnit+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+Dept +"</td>");
        returnValue.append("  </tr>");
        // PM
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nPM+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+sPMfullName +"</td>");
        returnValue.append("  </tr>");
        //Delay percent
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nDelay+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+dlyPercent +" % </td>");
        returnValue.append("  </tr>");
        // Dev Class
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nATNDevelopmentClass+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+projMap.get("ATNDevelopmentClass") +"</td>");
        returnValue.append("  </tr>");
        // Model
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nATNModelNumber+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+projMap.get("ATNModelNumber") +"</td>");
        returnValue.append("  </tr>");
        // Standard
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nATNStandard+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+projMap.get("ATNStandard") +"</td>");
        returnValue.append("  </tr>");
        // Basic Header
        returnValue.append("  <tr bgcolor=\"d6e4f4\">");
        returnValue.append("    <td height=\"40\" colspan=\"2\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nBasicInfo +"</strong></td>");
        returnValue.append("  </tr>");
        // ATN Phase Type
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nATNPhaseType+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+baseMap.get("ATNPhaseType") +"</td>");
        returnValue.append("  </tr>");
        // ATN Baseline Num
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nATNBaselineNum+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+baseMap.get("ATNBaselineNum") +"</td>");
        returnValue.append("  </tr>");
        // Description
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nBaseDesc+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+baseDesc +"</td>");
        returnValue.append("  </tr>");
        // Initial
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nBaseInit+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+baseInitStr +"</td>");
        returnValue.append("  </tr>");
        // Current
        returnValue.append("  <tr>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf; border-right:solid 1px #bdbebf;\"><strong>"+si18nBaseCur+"</strong></td>");
        returnValue.append("    <td height=\"40\" style=\"border-bottom:solid 1px #bdbebf\";>"+baseCurStr +"</td>");
        returnValue.append("  </tr>");
        // ATN NG
        returnValue.append("  <tr align=\"center\" bgcolor=\"d2d2d2\">");
        returnValue.append("    <td height=\"25\" colspan=\"2\"><span style=\"font:11px; color:#8f8a8a\";><strong>COPYRIGHT");
        returnValue.append("      (C) 2015 AUTONICS COMPANY. ALL RIGHTS RESERVED.</strong></span></td>");
        returnValue.append("  </tr>");
        returnValue.append("</table>");
        returnValue.append("</body>");
        returnValue.append("</html>");

        return returnValue.toString();
    }
    /**
     * 에노비아 날짜 스트링 출력
     * @param context
     * @param strDate
     * @return
     * @throws Exception
     */
    public static String convertDateFormat( Context context , String strDate )throws Exception{
        String ret = "";

        try{
            Date date = new Date(strDate);
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy. MM. dd");
            ret = sdf.format(date);
        }catch(Exception e){

        }

        return ret ;
    }

    /**
     * 에노비아 멀티라인 스트링 출력
     * @param str
     * @return
     * @throws Exception
     */
    public static String convertMultilineStr( String str )throws Exception{
        String displayStr = str;
        displayStr = FrameworkUtil.findAndReplace(displayStr, " ", "&nbsp;");
        displayStr = FrameworkUtil.findAndReplace(displayStr, "\n", "<br />");
        displayStr = FrameworkUtil.findAndReplace(displayStr, "\t", "&nbsp;&nbsp;&nbsp;&nbsp;");

        return displayStr;
    }


    /**
     * 신규 Task와 Baseline을 연결한다.
     * @param context
     * @param baselineObj
     * @param oldTasks
     * @throws Exception
     */
    private void copyBaselineConnection( Context context, DomainObject baselineObj, MapList newTasks, MapList oldTasks, StringList slSelectAllTask ) throws Exception
    {
        for(int iNew = 0 ; iNew<newTasks.size(); iNew++)
        {
            Map mNewTasks = (Map) newTasks.get(iNew);
            String strNewTaskId		= (String) mNewTasks.get(DomainConstants.SELECT_ID);
            String strNewTaskName	= (String) mNewTasks.get(DomainConstants.SELECT_NAME);
            String strNewParentName	= (String) mNewTasks.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

            DomainObject dmoNewTask = new DomainObject(strNewTaskId);

            for(int iOld=0; iOld<oldTasks.size(); iOld++)
            {
                Map mOldTasks = (Map) oldTasks.get(iOld);
                String strOldTaskId		= (String) mOldTasks.get(DomainConstants.SELECT_ID);
                String strOldTaskName	= (String) mOldTasks.get(DomainConstants.SELECT_NAME);
                String strOldParentName	= (String) mOldTasks.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

                if(slSelectAllTask.contains(strOldTaskId) && strOldParentName.equals(strNewParentName) && strOldTaskName.equals(strNewTaskName))
                {
                    if(!ATNDomainUtil.isAlreadyConnected(context, baselineObj, dmoNewTask, ATNConstants.RELATIONSHIP_ATNBASELINETASK))
                    {
                        DomainRelationship dmoRel = DomainRelationship.connect(context, baselineObj, ATNConstants.RELATIONSHIP_ATNBASELINETASK, dmoNewTask);

                        String oldTaskRevision 	= (String) mOldTasks.get(DomainConstants.SELECT_REVISION);
                        dmoRel.setAttributeValue(context, ATNConstants.ATTRIBUTE_ATNPREVIOUSTASKREVISION, oldTaskRevision);
                    }
                }
            }
        }
    }

    /**
     * Baseline 완료(Complete) 시 이전 차수의 Task 완료 상태로 변경
     * @param context
     * @param args
     * @throws Exception
     */
    public void completePreviousTask(Context context, String[] args) throws Exception
    {
        String baseLineId = args[0];

        try
        {
            DomainObject dmoBaseLine = DomainObject.newInstance(context, baseLineId);

            StringList typeSelects = new StringList(1);
            typeSelects.add(DomainConstants.SELECT_NAME);

            StringList relSelects = new StringList(1);
            relSelects.add("attribute[" + ATNConstants.ATTRIBUTE_ATNPREVIOUSTASKREVISION + "]");

            MapList mlTasks = dmoBaseLine.getRelatedObjects(context,
                    ATNConstants.RELATIONSHIP_ATNBASELINETASK,
                    ATNConstants.TYPE_TASK,
                    typeSelects,
                    relSelects,
                    false,
                    true,
                    (short)1,
                    null,
                    null,
                    null,
                    null,
                    null);

            int iSize = mlTasks.size();

            if(iSize > 0)
            {
                for(int iTask=iSize-1; iTask >= 0; iTask--)
                {
                    Map mTasks = (Map) mlTasks.get(iTask);
                    String taskName 			= (String) mTasks.get(DomainConstants.SELECT_NAME);
                    String previousTaskRevision = (String) mTasks.get("attribute[" + ATNConstants.ATTRIBUTE_ATNPREVIOUSTASKREVISION + "]");

                    if(UIUtil.isNotNullAndNotEmpty(previousTaskRevision))
                    {
                        String preTaskId = ATNDomainUtil.getObjectId(context, ATNConstants.TYPE_TASK, ATNConstants.SYMB_WILD, previousTaskRevision);

                        DomainObject dmoTask = new DomainObject(preTaskId);
                        String taskCurrent = dmoTask.getInfo(context, DomainConstants.SELECT_CURRENT);

                        if(!ProgramCentralConstants.STATE_PROJECT_TASK_COMPLETE.equals(taskCurrent))
                        {
                            completeSubtask(context, dmoTask);
                            dmoTask.setState(context, ProgramCentralConstants.STATE_PROJECT_TASK_COMPLETE);
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * 프로젝트 하위 (1Level)에 연결된 Phase 리스트를 CheckBox 형식으로 가져오기
     * 할당(Assign) 또는 진행중(Active)인 Phase 리스트만 가져 옴
     * @param context
     * @param args
     * @returns String HTML code
     * @throws Exception if the operation fails
     * @since Framework 10.6.0
     */
    public static String getOneLevelPhaseList(Context context, String[] args)
            throws Exception
    {
        String projectId = args[0];
        String attrName	 = args[1];
        String language	 = args[2];
        StringBuffer sb = new StringBuffer();

        StringList typeSelects = new StringList(2);
        typeSelects.add(ProgramCentralConstants.SELECT_NAME);
        typeSelects.add(ProgramCentralConstants.SELECT_CURRENT);

        StringList relSelects = new StringList(1);
        relSelects.add("attribute[" + ProgramCentralConstants.ATTRIBUTE_TASK_WBS + "]");

        String strWhere = ProgramCentralConstants.SELECT_CURRENT + "==" + ProgramCentralConstants.STATE_PROJECT_TASK_ASSIGN + "||" +
                ProgramCentralConstants.SELECT_CURRENT + "==" + ProgramCentralConstants.STATE_PROJECT_TASK_ACTIVE;

        DomainObject dmoProject = new DomainObject(projectId);

        MapList phaseList = dmoProject.getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                ProgramCentralConstants.TYPE_PHASE,
                typeSelects,
                relSelects,
                false,
                true,
                (short)1,
                strWhere,
                null,
                0);

        phaseList.sort("attribute[" + ProgramCentralConstants.ATTRIBUTE_TASK_WBS + "]", "ascending", "string");

        if(phaseList != null && phaseList.size() > 0)
        {
            for(int iPhase=0; iPhase<phaseList.size(); iPhase++)
            {
                Map mPhase = (Map) phaseList.get(iPhase);
                String strPhaseName = (String) mPhase.get(ProgramCentralConstants.SELECT_NAME);

                sb.append("<input type=\"checkbox\" name=\"");
                sb.append(attrName);
                sb.append("\" id=\"");
                sb.append(attrName);
                sb.append("\" value=\"");
                sb.append(strPhaseName);
                sb.append("\"");
                sb.append("\">");
                sb.append("&nbsp;");
                sb.append(strPhaseName);
                sb.append("&nbsp;");
            }
        }

        return sb.toString();
    }

    /**
     * 선택한 Task 중 해당 Phase에 연결된 Task만 넘겨준다.
     * @param context
     * @param topId
     * @param ATNPhaseType
     * @return
     * @throws Exception
     */
    private String[] getPhaseConnectedSelectTasks(Context context, Task phase, String[] selectedIds) throws Exception
    {
        int iArry = 0;

        List<String> mList = new ArrayList<String>();
        String strPhaseWBS = phase.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_SUBTASK + "].attribute[" + ATNConstants.ATTRIBUTE_TASK_WBS + "]");

        for(int iTask=0; iTask<selectedIds.length; iTask++)
        {
            StringList slTask = FrameworkUtil.split(selectedIds[iTask], "|");
            String strTaskWBS = (String) slTask.get(0);

            if(strTaskWBS.startsWith(strPhaseWBS))
            {
                mList.add((String) slTask.get(1));
                iArry++;
            }
        }

        String[] arryTaskId = new String[mList.size()];
        for(int i=0; i<mList.size(); i++)
        {
            arryTaskId[i] = mList.get(i);
        }

        return arryTaskId;
    }

    /**
     * Baseline 완료(Complete) 시 BaselineEstimatedDate Object 생성
     * Phase와 Gate의 예상 시작일과 예상 종료일을 관리
     * @param context
     * @param args
     * @throws Exception
     */
    public void createBaselineEstimatedDate(Context context, String[] args) throws Exception {
        String strBaselineId = args[0];
        String strBaseName	 = args[1];

        try {
            DomainObject dmoBaseline = DomainObject.newInstance(context, strBaselineId);
            String strProjectId = dmoBaseline.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_BASELINE_LOG + "].from.id" );

            DomainObject dmoProject = DomainObject.newInstance(context, strProjectId);

            Pattern typePattern = new Pattern(ProgramCentralConstants.TYPE_PHASE);
            typePattern.addPattern(ProgramCentralConstants.TYPE_GATE);
            typePattern.addPattern(ProgramCentralConstants.TYPE_TASK);

            StringList typeSelects = new StringList(6);
            typeSelects.add(ProgramCentralConstants.SELECT_TYPE);
            typeSelects.add(ProgramCentralConstants.SELECT_NAME);
            typeSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNPHASEDIVISION);
            typeSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNDEVCOMPLETEFLAG);
            typeSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE);
            typeSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE);

            StringList relSelects = new StringList(1);
            relSelects.add("attribute[" + ProgramCentralConstants.ATTRIBUTE_SEQUENCE_ORDER + "]");

            MapList mlSubtask = dmoProject.getRelatedObjects(context,
                    ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                    typePattern.getPattern(),
                    typeSelects,
                    relSelects,
                    false,
                    true,
                    (short) 1,
                    DomainObject.EMPTY_STRING,
                    DomainObject.EMPTY_STRING,
                    0);

            int iSubtaskSize = mlSubtask.size();

            if(iSubtaskSize > 0) {
                for(int i=0; i<iSubtaskSize; i++) {
                    Map mSubtask			= (Map) mlSubtask.get(i);
                    String strSubtaskType	= (String) mSubtask.get(ProgramCentralConstants.SELECT_TYPE);
                    String strSubtaskName	= (String) mSubtask.get(ProgramCentralConstants.SELECT_NAME);
                    String strPhaseDivision	= (String) mSubtask.get(ATNConstants.SELECT_ATTRIBUTE_ATNPHASEDIVISION);
                    String strDevFlag		= (String) mSubtask.get(ATNConstants.SELECT_ATTRIBUTE_ATNDEVCOMPLETEFLAG);
                    String strEstStartDate	= (String) mSubtask.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE);
                    String strEstFinishDate	= (String) mSubtask.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE);
                    String strSequenceOrder	= (String) mSubtask.get("attribute[" + ProgramCentralConstants.ATTRIBUTE_SEQUENCE_ORDER + "]");

                    if(ProgramCentralConstants.TYPE_PHASE.equals(strSubtaskType) || ProgramCentralConstants.TYPE_GATE.equals(strSubtaskType)
                            || (ProgramCentralConstants.TYPE_TASK.equals(strSubtaskType) && "TRUE".equals(strDevFlag))) {

                        Map attrMap = new HashMap();
                        attrMap.put(ProgramCentralConstants.ATTRIBUTE_TASK_ESTIMATED_START_DATE, strEstStartDate);
                        attrMap.put(ProgramCentralConstants.ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE, strEstFinishDate);
                        attrMap.put(ProgramCentralConstants.ATTRIBUTE_SEQUENCE_ORDER, strSequenceOrder);

                        if(UIUtil.isNotNullAndNotEmpty(strPhaseDivision)) {
                            attrMap.put(ATNConstants.ATTRIBUTE_ATNPHASEDIVISION, strPhaseDivision);
                        }

                        DomainObject dmoBaselineDate = DomainObject.newInstance(context);
                        dmoBaselineDate.createObject(context, ATNConstants.TYPE_ATNBASELINEESTIMATEDDATE, strSubtaskName, strBaseName, ATNConstants.POLICY_ATNBASELINEESTIMATEDDATE, ATNConstants.VAULT_ESERVICE_PRODUCTION);
                        dmoBaselineDate.setAttributeValues(context, attrMap);
                        dmoBaselineDate.setDescription(context, strSubtaskType);

                        DomainRelationship.connect(context, dmoBaseline, ATNConstants.RELATIONSHIP_ATNBASELINEESTIMATEDDATE, dmoBaselineDate);
                    }
                }
            }

            // Baseline Object에 프로젝트의 예상시작일, 예상종료일 값 저장
            Map projectInfo = dmoProject.getInfo(context, typeSelects);

            Map projectAttrMap = new HashMap();
            projectAttrMap.put(ATNConstants.ATTRIBUTE_ATNPROJECTESTIMATEDSTARTDATE, projectInfo.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE));
            projectAttrMap.put(ATNConstants.ATTRIBUTE_ATNPROJECTESTIMATEDFINISHDATE, projectInfo.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE));

            dmoBaseline.setAttributeValues(context, projectAttrMap);
        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Baseline의 차수를 기준으로 프로젝트의 Phase, Gate 예상시작일/예상종료일 정보 컬럼
     * @param context
     * @param args
     * @throws Exception
     */
    public MapList getDynamicBaselineEstimatedDate(Context context, String[] args) throws Exception{

        try
        {
            MapList mlColumns = new MapList();
            Map programMap = (Map) JPO.unpackArgs(args);
            Map requestMap = (Map) programMap.get("requestMap");
            String strObjectId = (String) requestMap.get("objectId");
            boolean bDate = false;

            /* 컬럼 라벨 정의 */
            String strEstStartDate	= "emxProgramCentral.Common.EstStartDate";
            String strEstFinishDate	= "emxProgramCentral.Common.EstimatedFinishDate";

            DomainObject dmoProject = new DomainObject(strObjectId);

            String dojType = dmoProject.getType(context);
            if(dojType.equals(ATNConstants.TYPE_ATNBASELINELOG)){
                String strBaselineRelProject = dmoProject.getInfo(context, "to["+ATNConstants.RELATIONSHIP_BASELINE_LOG+"].from.id");
                dmoProject.setId(strBaselineRelProject);
            }

            /* 프로젝트에 연결된 Phase, Gate */
            Pattern typePattern = new Pattern(ProgramCentralConstants.TYPE_PHASE);
            typePattern.addPattern(ProgramCentralConstants.TYPE_GATE);

            StringList typeSelects = new StringList(4);
            typeSelects.add(ProgramCentralConstants.SELECT_ID);
            typeSelects.add(ProgramCentralConstants.SELECT_NAME);
            typeSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE);
            typeSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE);

            StringList relSelects = new StringList(1);
            relSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_SEQUENCE_ORDER);

            MapList mlSubtask = dmoProject.getRelatedObjects(context,
                    ProgramCentralConstants.RELATIONSHIP_SUBTASK,
                    typePattern.getPattern(),
                    typeSelects,
                    relSelects,
                    false,
                    true,
                    (short)1,
                    null,
                    null,
                    0);

            mlSubtask.sort(ProgramCentralConstants.SELECT_ATTRIBUTE_SEQUENCE_ORDER, "ascending", "integer");

            if(mlSubtask.size()>0)
            {
                for(int iSub=0 ; iSub<mlSubtask.size() ; iSub++)
                {
                    Map map = (Map)mlSubtask.get(iSub);
                    String strSubtaskName = (String) map.get(ProgramCentralConstants.SELECT_NAME);

                    /* 테이블 settings 정의 */
                    Map mSettings = new HashMap(10);
                    mSettings.put("Registered Suite", "ProgramCentral");
                    mSettings.put("program", "ATNBaseline");
                    mSettings.put("function", "getEstimatedDate");
                    mSettings.put("Column Type", "program");
                    mSettings.put("Field Type", "attribute");
                    //mSettings.put("format","date");
                    mSettings.put("Editable", "false");
                    mSettings.put("Sortable", "false");
                    mSettings.put("Width", "30");
                    mSettings.put("Export", "true");
                    //mSettings.put("Nowrap", "true");
                    mSettings.put("Group Header", strSubtaskName);
                    mSettings.put("subtaskList", mlSubtask);

                    /* 계획시작일 컬럼 정의 */
                    Map mColumn = new HashMap(4);
                    mColumn.put("name", "StartDate_" + strSubtaskName);
                    mColumn.put("label", strEstStartDate);
                    mColumn.put("sorttype", "none");
                    mColumn.put("settings", mSettings);

                    mlColumns.add(mColumn);

                    /* 계획종료일 컬럼 정의 */
                    Map mColumn2 = new HashMap(4);
                    mColumn2.put("name", "FinishDate_" + strSubtaskName);
                    mColumn2.put("label", strEstFinishDate);
                    mColumn2.put("sorttype", "none");
                    mColumn2.put("settings", mSettings);

                    mlColumns.add(mColumn2);
                }
            }

            return mlColumns;
        }
        catch(Exception ex)
        {
            throw new MatrixException(ex);
        }
    }

    /**
     * Baseline의 차수를 기준으로 프로젝트의 Phase, Gate 예상시작일/예상종료일 정보 컬럼
     * @param context
     * @param args
     * @throws Exception
     */
    public Vector getEstimatedDate(Context context, String[] args) throws Exception{

        Vector result = new Vector();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy. M. d", Locale.US);
        SimpleDateFormat sdf2 = new SimpleDateFormat(eMatrixDateFormat.getEMatrixDateFormat(), Locale.US);

        Map programMap		= (Map) JPO.unpackArgs(args);
        HashMap paramList 	= (HashMap) programMap.get("paramList");
        HashMap ColumnMap	= (HashMap) programMap.get("columnMap");
        HashMap Settings	= (HashMap) ColumnMap.get("settings");

        String strObjectId		= (String) paramList.get("objectId");
        String strColumnName	= (String) ColumnMap.get("name");
        String strGroupHeader	= (String) Settings.get("Group Header");
        MapList subtaskList		= (MapList) Settings.get("subtaskList");
        String strDate			= "";

        DomainObject dmoProject = new DomainObject(strObjectId);

        String dojType = dmoProject.getType(context);
        if(dojType.equals(ATNConstants.TYPE_ATNBASELINELOG)){
            String strBaselineRelProject = dmoProject.getInfo(context, "to["+ATNConstants.RELATIONSHIP_BASELINE_LOG+"].from.id");
            dmoProject.setId(strBaselineRelProject);
        }

        /* 프로젝트에 연결된 Baseline */
        StringList typeSelects = new StringList(3);
        typeSelects.add(ProgramCentralConstants.SELECT_ID);
        typeSelects.add(ProgramCentralConstants.SELECT_NAME);
        typeSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);

        MapList mlBaseline = dmoProject.getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_BASELINE_LOG,
                ATNConstants.TYPE_ATNBASELINELOG,
                typeSelects,
                null,
                false,
                true,
                (short)1,
                null,
                null,
                0);

        mlBaseline.sort(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM, "ascending", "integer");

        if(mlBaseline.size()>0)
        {
            for(int iBase=0 ; iBase<mlBaseline.size() ; iBase++)
            {
                Map mBaseline = (Map) mlBaseline.get(iBase);
                String strBaselineId = (String) mBaseline.get(ProgramCentralConstants.SELECT_ID);

                /* Phase, Gate 날짜 */
                DomainObject dmoBaseline = new DomainObject(strBaselineId);

                typeSelects = new StringList(2);
                typeSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE);
                typeSelects.add(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE);

                String swhere = ProgramCentralConstants.SELECT_NAME + "=='" + strGroupHeader + "'";

                MapList mlBaselineDate = dmoBaseline.getRelatedObjects(context,
                        ATNConstants.RELATIONSHIP_ATNBASELINEESTIMATEDDATE,
                        ATNConstants.TYPE_ATNBASELINEESTIMATEDDATE,
                        typeSelects,
                        null,
                        false,
                        true,
                        (short)1,
                        swhere,
                        null,
                        0);

                strDate = "";
                if(mlBaselineDate.size() > 0)
                {
                    Map mBaselineDate = (Map) mlBaselineDate.get(0);

                    if(strColumnName.charAt(0) == 'S')
                    {
                        strDate = (String) mBaselineDate.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE);
                    }
                    else
                    {
                        strDate = (String) mBaselineDate.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE);
                    }
                } else if(iBase != 0) {
                    for(int iSub=0; iSub<subtaskList.size(); iSub++) {
                        Map mSubtask = (Map) subtaskList.get(iSub);
                        String sSubtaskName = (String) mSubtask.get(ProgramCentralConstants.SELECT_NAME);

                        if(sSubtaskName.equals(strGroupHeader)) {
                            if(strColumnName.charAt(0) == 'S')
                            {
                                strDate = (String) mSubtask.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_START_DATE);
                            }
                            else
                            {
                                strDate = (String) mSubtask.get(ProgramCentralConstants.SELECT_ATTRIBUTE_TASK_ESTIMATED_FINISH_DATE);
                            }
                        }
                    }
                }

                if(UIUtil.isNotNullAndNotEmpty(strDate)) {
                    strDate = sdf.format(sdf2.parse(strDate));
                }

                result.add(strDate);
            }
        }

        return result;
    }

    /**
     * Baseline 삭제 시 해당 차수에 연결된 Task도 삭제
     * @param context
     * @param args
     * @throws Exception
     */
    public String deleteBaseline(Context context, String[] args) throws Exception {

        Map programMap	= (Map) JPO.unpackArgs(args);
        String[] arryTableRowId		= (String[]) programMap.get("emxTableRowIds");
        String[] arryDeleteObject	= null;
        String strProjectId	= (String) programMap.get("projectId");
        String strErrorMsg	= "";
        Locale strLocale = new Locale(context.getSession().getLanguage());

        com.matrixone.apps.common.Task task =
                (com.matrixone.apps.common.Task) DomainObject.newInstance(context, DomainConstants.TYPE_TASK);

        try
        {
            ContextUtil.startTransaction(context, true);

            for(int iRow=0; iRow<arryTableRowId.length; iRow++)
            {
                String strTableRow = arryTableRowId[iRow];
                StringList slInfo = FrameworkUtil.split(strTableRow, "|");

                String strBaselineId = (String) slInfo.get(1);

                DomainObject dmoBaseline = new DomainObject(strBaselineId);
                String strBaselineCurrent = dmoBaseline.getInfo(context, ATNConstants.SELECT_CURRENT);

                if(!ATNConstants.STATE_ATNBASELINELOG_CREATE.equals(strBaselineCurrent))
                {
                    strErrorMsg = EnoviaResourceBundle.getProperty(context, "emxProgramCentralStringResource", strLocale, "emxProgramCentral.ErrorMessage.CannotDeleteBaseline");
                    break;
                }
                else
                {
                    StringList typeSelects = new StringList(2);
                    typeSelects.add(ATNConstants.SELECT_ID);
                    typeSelects.add(ATNConstants.SELECT_CURRENT);
                    typeSelects.add(SELECT_ATTRIBUTE_ATNWORKBASELINENUM);
                    typeSelects.add(SELECT_ATTRIBUTE_ATNBEFORBASELINETASKFLAG);

                    StringList relationshipSelects = new StringList(3);
                    relationshipSelects.addElement(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);

                    /* 베이스라인에 연결된 타스크  */
                    MapList mlTask = dmoBaseline.getRelatedObjects(context,
                            ATNConstants.RELATIONSHIP_ATNBASELINETASK,
                            ATNConstants.TYPE_TASK,
                            typeSelects,
                            relationshipSelects,
                            false,
                            true,
                            (short)1,
                            null,
                            null,
                            0);

                    int iSize = mlTask.size();

                    if(iSize > 0)
                    {
                        for(int iTask=iSize-1; iTask >= 0; iTask--)
                        {
                            Map mTask = (Map) mlTask.get(iTask);
                            String strTaskId		= (String) mTask.get(ATNConstants.SELECT_ID);
                            String strTaskCurrent	= (String) mTask.get(ATNConstants.SELECT_CURRENT);
                            String strWorkBaselineNum = (String) mTask.get(SELECT_ATTRIBUTE_ATNWORKBASELINENUM);
                            String strPreviorsTaskRevision = (String) mTask.get(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);
                            if(UIUtil.isNotNullAndNotEmpty(strPreviorsTaskRevision)){
                                String strBeforTaskId = ATNDomainUtil.getObjectId(context, ATNConstants.TYPE_TASK, ATNConstants.SYMB_WILD, strPreviorsTaskRevision);

                                //String temp = MqlUtil.mqlCommand(context, "temp query bus $1 $2 $3 select $4 dump", ATNConstants.TYPE_TASK, "*", strPreviorsTaskRevision, "id");
                                //String strBeforTask = temp.substring(temp.lastIndexOf('|')+1);
                                //String strBeforTaskId = temp.split(",")[3];
                                //System.out.println(strBeforTaskId);

                                Map attrMap = new HashMap(2);
                                attrMap.put(ATTRIBUTE_ATNWORKBASELINENUM, "0");
                                attrMap.put(ATTRIBUTE_ATNBEFORBASELINETASKFLAG, "N");

                                // 연결되어 있는 베이스라인 번호 입력

                                // MaxbaselineNumber보다 작으면 Y로 입력


                                DomainObject doBeforTask = new DomainObject(strBeforTaskId);
                                doBeforTask.setAttributeValues(context, attrMap);

                            }
                            if(!ProgramCentralConstants.STATE_PROJECT_TASK_COMPLETE.equals(strTaskCurrent))
                            {
                                task.setId(strTaskId);
                                task.delete(context, true);
                            }

                        }
                    }

                    dmoBaseline.deleteObject(context);
                }
            }
			/*StringList objectSelects = new StringList(10);
			objectSelects.addElement(DomainConstants.SELECT_ID);
			objectSelects.addElement(DomainConstants.SELECT_NAME);

			StringList relationshipSelects = new StringList(3);
			relationshipSelects.addElement(DomainConstants.SELECT_RELATIONSHIP_ID);
			relationshipSelects.addElement(DomainConstants.SELECT_LEVEL);
			relationshipSelects.addElement(SubtaskRelationship.SELECT_SEQUENCE_ORDER);
			relationshipSelects.addElement("from.id");//Added for "What if"
			relationshipSelects.addElement(DomainConstants.SELECT_RELATIONSHIP_NAME);

			//Query parameters
			String typePattern 		= ProgramCentralConstants.TYPE_TASK;
			String direction 		= "from";
			String busWhereClause 	= null;
			String relWhereClause 	= null;

			DomainObject dProject = new DomainObject(strProjectId);
			MapList objectList = dProject.getRelatedObjects(context,
					ATNConstants.RELATIONSHIP_SUBTASK,
					typePattern,
					objectSelects,
					relationshipSelects,
					false,
					true,
					(short) 0,
					busWhereClause,
					relWhereClause);

			System.out.println(objectList);*/

            task.setId(strProjectId);
            task.rollupAndSave(context);

            ContextUtil.commitTransaction(context);
        }
        catch(Exception ex)
        {
            ContextUtil.abortTransaction(context);
            throw new Exception(ex);
        }

        return strErrorMsg;
    }

    /**
     * Baseline 완료(Complete) 시  이전 차수의 Task에 연결된 산출물을 현재 차수의 Task로 이동
     * @param context
     * @param args
     * @throws Exception
     */
    public void moveDeliverable(Context context, String[] args) throws Exception
    {
        String baseLineId = args[0];

        try
        {
            DomainObject dmoBaseLine = DomainObject.newInstance(context, baseLineId);

            StringList typeSelects = new StringList(3);
            typeSelects.add(DomainConstants.SELECT_ID);
            typeSelects.add(DomainConstants.SELECT_TYPE);
            typeSelects.add(DomainConstants.SELECT_NAME);

            StringList relSelects = new StringList(2);
            relSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);
            relSelects.add(ATNConstants.SELECT_RELATIONSHIP_ID);

            StringList relType = new StringList(2);
            relType.add(ATNConstants.RELATIONSHIP_TASK_DELIVERABLE);
            relType.add(ATNConstants.RELATIONSHIP_ATNSTANDARDDELIVERABLE);

            MapList mlTasks = dmoBaseLine.getRelatedObjects(context,
                    ATNConstants.RELATIONSHIP_ATNBASELINETASK,
                    ATNConstants.TYPE_TASK,
                    typeSelects,
                    relSelects,
                    false,
                    true,
                    (short)1,
                    null,
                    null,
                    0);

            if(mlTasks.size() > 0)
            {
                connectAnddisconnectDeliverable(context, mlTasks, typeSelects, relSelects, relType);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * Baseline 완료(Complete) 시  이전 차수의 Task에 연결된 산출물을 현재 차수의 Task로 이동
     * @param context
     * @param args
     * @throws Exception
     */
    public void connectAnddisconnectDeliverable(Context context, MapList mlTasks, StringList typeSelects, StringList relSelects, StringList relType) throws Exception
    {

        try
        {
            for(int iRel=0; iRel<relType.size(); iRel++)
            {
                String strRelName = (String) relType.get(iRel);

                for(int iTask=0; iTask<mlTasks.size(); iTask++)
                {
                    Map mTasks = (Map) mlTasks.get(iTask);
                    String strTaskId			= (String) mTasks.get(DomainConstants.SELECT_ID);
                    String strTaskType			= (String) mTasks.get(DomainConstants.SELECT_TYPE);
                    String strTaskName			= (String) mTasks.get(DomainConstants.SELECT_NAME);
                    String strPreTaskRevision	= (String) mTasks.get(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);

                    if(UIUtil.isNotNullAndNotEmpty(strPreTaskRevision))
                    {
                        String preTaskId = ATNDomainUtil.getObjectId(context, strTaskType, ATNConstants.SYMB_WILD, strPreTaskRevision);

                        DomainObject dmoTask = new DomainObject(strTaskId);
                        DomainObject dmoPreTask = new DomainObject(preTaskId);

                        MapList mlDeliver = dmoPreTask.getRelatedObjects(context,
                                strRelName,
                                "*",
                                typeSelects,
                                relSelects,
                                false,
                                true,
                                (short)1,
                                null,
                                null,
                                0);

                        if(mlDeliver.size() > 0)
                        {
                            for(int i=0; i<mlDeliver.size(); i++)
                            {
                                Map mDeliver = (Map) mlDeliver.get(i);
                                String strDeliverId		= (String) mDeliver.get(DomainConstants.SELECT_ID);
                                String strDeliverRelId	= (String) mDeliver.get(ATNConstants.SELECT_RELATIONSHIP_ID);

                                DomainObject dmoDeliver = new DomainObject(strDeliverId);
                                // if not connected , then connect
                                if( !ATNDomainUtil.isAlreadyConnected(context, dmoTask, dmoDeliver, strRelName))
                                {
                                    DomainRelationship rel = DomainRelationship.connect(context, dmoTask, strRelName, dmoDeliver);

                                    if(ATNConstants.RELATIONSHIP_ATNSTANDARDDELIVERABLE.equals(strRelName))
                                    {
                                        rel.setAttributeValue(context, ATNConstants.ATTRIBUTE_ATNSTDREQUIRED, "Y");
                                    }
                                }

                                /* 진행중인 Task의 산출물을 제거할 수 없어 Trigger를 끄고 진행 */
                                MqlUtil.mqlCommand(context, "trigger off", true, false);
                                DomainRelationship.disconnect(context, strDeliverRelId);
                                MqlUtil.mqlCommand(context, "trigger on", true, false);
                            }
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 신규 Task의 Dependency를 구성
     * @param context
     * @param newTaskMap
     * @param oldTasks
     * @throws Exception
     */
    private void copyDependencyNewTasks(Context context, DomainObject baselineObj, MapList newTasks, MapList oldTasks, StringList slSelectAllTask) throws Exception
    {
        for(int iNew = 0 ; iNew<newTasks.size(); iNew++)
        {
            Map mNewTasks = (Map) newTasks.get(iNew);
            String strNewTaskId		= (String) mNewTasks.get(DomainConstants.SELECT_ID);
            String strNewTaskName	= (String) mNewTasks.get(DomainConstants.SELECT_NAME);
            String strNewParentName	= (String) mNewTasks.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

            DomainObject dmoNewTask = new DomainObject(strNewTaskId);

            for(int iOld=0; iOld<oldTasks.size(); iOld++)
            {
                Map mOldTasks = (Map) oldTasks.get(iOld);
                String strOldTaskId		= (String) mOldTasks.get(DomainConstants.SELECT_ID);
                String strOldTaskName	= (String) mOldTasks.get(DomainConstants.SELECT_NAME);
                String strOldParentName	= (String) mOldTasks.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

                if(slSelectAllTask.contains(strOldTaskId) && strOldParentName.equals(strNewParentName) && strOldTaskName.equals(strNewTaskName))
                {
                    DomainObject dmoOldTask = new DomainObject(strOldTaskId);

                    Pattern typePattern = new Pattern(ProgramCentralConstants.TYPE_PHASE);
                    typePattern.addPattern(ProgramCentralConstants.TYPE_GATE);
                    typePattern.addPattern(ProgramCentralConstants.TYPE_TASK);

                    StringList typeSelects = new StringList(4);
                    typeSelects.add(ProgramCentralConstants.SELECT_ID);
                    typeSelects.add(ProgramCentralConstants.SELECT_NAME);
                    typeSelects.add(ProgramCentralConstants.SELECT_REVISION);
                    typeSelects.add("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

                    StringList relSelects = new StringList(2);
                    relSelects.add("attribute[" + ProgramCentralConstants.ATTRIBUTE_LAG_TIME + "]");
                    relSelects.add("attribute[" + ProgramCentralConstants.ATTRIBUTE_DEPENDENCY_TYPE + "]");

                    /* 이전 차수의 Task와 Dependency로 연결된 object를 찾는다 */
                    MapList mlDependencyOldTask = dmoOldTask.getRelatedObjects(context,
                            ProgramCentralConstants.RELATIONSHIP_DEPENDENCY,
                            typePattern.getPattern(),
                            typeSelects,
                            relSelects,
                            false,
                            true,
                            (short)1,
                            null,
                            null,
                            0);

                    if(mlDependencyOldTask.size()>0)
                    {
                        for(int iDepOld=0; iDepOld<mlDependencyOldTask.size(); iDepOld++)
                        {
                            Map mDepOldTask = (Map) mlDependencyOldTask.get(iDepOld);
                            String strDepTaskId			= (String) mDepOldTask.get(ProgramCentralConstants.SELECT_ID);
                            String strDepTaskName		= (String) mDepOldTask.get(ProgramCentralConstants.SELECT_NAME);
                            String strDepTaskRevision	= (String) mDepOldTask.get(ProgramCentralConstants.SELECT_REVISION);
                            String strDepTaskParentName = (String) mDepOldTask.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);
                            String strDepTaskLagTime 	= (String) mDepOldTask.get("attribute[" + ProgramCentralConstants.ATTRIBUTE_LAG_TIME + "]");
                            String strDepTaskDepType 	= (String) mDepOldTask.get("attribute[" + ProgramCentralConstants.ATTRIBUTE_DEPENDENCY_TYPE + "]");

                            if(slSelectAllTask.contains(strDepTaskId))
                            {
                                for(int iDepNew=0; iDepNew<newTasks.size(); iDepNew++)
                                {
                                    Map mDepNewTask = (Map) newTasks.get(iDepNew);

                                    String strDepNewTaskId		= (String) mDepNewTask.get(DomainConstants.SELECT_ID);
                                    String strDepNewTaskName	= (String) mDepNewTask.get(DomainConstants.SELECT_NAME);
                                    String strDepNewParentName	= (String) mDepNewTask.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

                                    if(strDepTaskParentName.equals(strDepNewParentName) && strDepTaskName.equals(strDepNewTaskName))
                                    {
                                        strDepTaskId = strDepNewTaskId;
                                    }
                                }
                            }

                            DomainObject dmoDepTask = new DomainObject(strDepTaskId);

                            if(!ATNDomainUtil.isAlreadyConnected(context, dmoNewTask, dmoDepTask, ProgramCentralConstants.RELATIONSHIP_DEPENDENCY))
                            {
                                DomainRelationship dmoRel = DomainRelationship.connect(context, dmoNewTask, ProgramCentralConstants.RELATIONSHIP_DEPENDENCY, dmoDepTask);

                                Map attrMap = new HashMap(2);
                                attrMap.put(ProgramCentralConstants.ATTRIBUTE_LAG_TIME, strDepTaskLagTime);
                                attrMap.put(ProgramCentralConstants.ATTRIBUTE_DEPENDENCY_TYPE, strDepTaskDepType);

                                dmoRel.setAttributeValues(context, attrMap);
                            }
                        }
                    }
                }
            }
        }
    }

    /**
     * 기존 타스크의 산출물 연결을 복사하여 연결한다.
     * @param context
     * @param newTaskMap
     * @param oldTasks
     * @throws Exception
     */
    private void copyTaskDeliverableConnection( Context context, Map newTaskMap , MapList oldTasks , StringList slSelectAllTask ) throws Exception
    {
        String newId			= (String)newTaskMap.get(DomainConstants.SELECT_ID);
        String newName			= (String)newTaskMap.get(DomainConstants.SELECT_NAME);
        String newParentName 	= (String) newTaskMap.get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

        DomainObject newTask = new DomainObject(newId);

        for( int i = 0 ; i < oldTasks.size() ; i++  ){

            String taskName			= (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_NAME);
            String oldId 			= (String)((Map)oldTasks.get(i)).get(DomainConstants.SELECT_ID);
            String odlParentName 	= (String)((Map)oldTasks.get(i)).get("to[" + ProgramCentralConstants.RELATIONSHIP_SUBTASK + "].from." + DomainConstants.SELECT_NAME);

            if(slSelectAllTask.contains(oldId) && odlParentName.equals(newParentName) && taskName.equals(newName)){

                StringList typeSelects = new StringList(2);
                typeSelects.add(CommonDocument.SELECT_ID);
                typeSelects.add(CommonDocument.SELECT_NAME);

                StringList relSelects = new StringList(1);
                relSelects.add(CommonDocument.SELECT_RELATIONSHIP_ID);

                MapList stdDelList = new DomainObject(oldId).getRelatedObjects(context,
                        ATNConstants.RELATIONSHIP_TASK_DELIVERABLE,
                        "*",
                        typeSelects,
                        relSelects,
                        false,
                        true,
                        (short)1,
                        "",
                        null,
                        null,
                        null,
                        null);

                for( int j = 0 ; j < stdDelList.size() ; j++ ){

                    DomainObject stdObj = new DomainObject( (String)((Map)stdDelList.get(j)).get(DomainConstants.SELECT_ID));
                    // if not connected , then connect
                    if( !ATNDomainUtil.isAlreadyConnected(context, newTask, stdObj, ATNConstants.RELATIONSHIP_TASK_DELIVERABLE)){
                        DomainRelationship rel = DomainRelationship.connect(context, newTask, ATNConstants.RELATIONSHIP_TASK_DELIVERABLE, stdObj );
                    }
                }

                // 최초 타스크명이 매칭되는 항목만 적용하기 위해 브레이크 처리한다.
                break;
            }
        }
    }

    /**
     * Baseline 완료(Complete) 시 이전 차수의 멤버 제거
     * @param context
     * @param args
     * @throws Exception
     */
    public void removeMemberPreviousTask(Context context, String[] args) throws Exception
    {
        String baseLineId = args[0];

        try
        {
            DomainObject dmoBaseLine = DomainObject.newInstance(context, baseLineId);

            StringList typeSelects = new StringList(3);
            typeSelects.add(DomainConstants.SELECT_ID);
            typeSelects.add(DomainConstants.SELECT_TYPE);
            typeSelects.add(DomainConstants.SELECT_NAME);

            StringList relSelects = new StringList(2);
            relSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);
            relSelects.add(ATNConstants.SELECT_RELATIONSHIP_ID);

            StringList relType = new StringList(2);
            relType.add(ATNConstants.RELATIONSHIP_ASSIGNED_TASKS);

            MapList mlTasks = dmoBaseLine.getRelatedObjects(context,
                    ATNConstants.RELATIONSHIP_ATNBASELINETASK,
                    ATNConstants.TYPE_TASK,
                    typeSelects,
                    relSelects,
                    false,
                    true,
                    (short)1,
                    null,
                    null,
                    0);

            if(mlTasks.size() > 0)
            {
                disconnectMember(context, mlTasks, typeSelects, relSelects, relType);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Baseline 완료(Complete) 시 이전 차수의 멤버 제거
     * @param context
     * @param args
     * @throws Exception
     */
    public void disconnectMember(Context context, MapList mlTasks, StringList typeSelects, StringList relSelects, StringList relType) throws Exception
    {

        try
        {
            for(int iRel=0; iRel<relType.size(); iRel++)
            {
                String strRelName = (String) relType.get(iRel);

                for(int iTask=0; iTask<mlTasks.size(); iTask++)
                {
                    Map mTasks = (Map) mlTasks.get(iTask);
                    String strTaskId			= (String) mTasks.get(DomainConstants.SELECT_ID);
                    String strTaskType			= (String) mTasks.get(DomainConstants.SELECT_TYPE);
                    String strTaskName			= (String) mTasks.get(DomainConstants.SELECT_NAME);
                    String strPreTaskRevision	= (String) mTasks.get(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);

                    if(UIUtil.isNotNullAndNotEmpty(strPreTaskRevision))
                    {
                        String preTaskId = ATNDomainUtil.getObjectId(context, strTaskType, ATNConstants.SYMB_WILD, strPreTaskRevision);

                        DomainObject dmoTask = new DomainObject(strTaskId);
                        DomainObject dmoPreTask = new DomainObject(preTaskId);

                        MapList mlDeliver = dmoPreTask.getRelatedObjects(context,
                                strRelName,
                                "*",
                                typeSelects,
                                relSelects,
                                true,
                                false,
                                (short)1,
                                null,
                                null,
                                0);

                        if(mlDeliver.size() > 0)
                        {
                            for(int i=0; i<mlDeliver.size(); i++)
                            {
                                Map mDeliver = (Map) mlDeliver.get(i);
                                String strDeliverId		= (String) mDeliver.get(DomainConstants.SELECT_ID);
                                String strDeliverRelId	= (String) mDeliver.get(ATNConstants.SELECT_RELATIONSHIP_ID);

                                /* 진행중인 Task의 산출물을 제거할 수 없어 Trigger를 끄고 진행 */
                                MqlUtil.mqlCommand(context, "trigger off", true, false);
                                DomainRelationship.disconnect(context, strDeliverRelId);
                                MqlUtil.mqlCommand(context, "trigger on", true, false);
                            }
                        }
                    }
                }
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 하위에 완료되지 않은 Task가 있으면 완료 처리
     * @param context
     * @param dmoTask
     * @throws Exception
     */
    private void completeSubtask(Context context, DomainObject dmoTask) throws Exception {
        try {
            MapList mlSubtask = dmoTask.getRelatedObjects(context, ProgramCentralConstants.RELATIONSHIP_SUBTASK, ProgramCentralConstants.TYPE_TASK,
                    new StringList(ProgramCentralConstants.SELECT_ID), ProgramCentralConstants.EMPTY_STRINGLIST, false, true, (short) 0,
                    ProgramCentralConstants.SELECT_CURRENT + " != " + ProgramCentralConstants.STATE_PROJECT_TASK_COMPLETE, ProgramCentralConstants.EMPTY_STRING, 0);

            int iSize = mlSubtask.size();

            if(iSize > 0) {
                DomainObject task = new DomainObject();

                for(int i=0; i<mlSubtask.size(); i++) {
                    String taskId =  (String) ((Map) mlSubtask.get(i)).get(ProgramCentralConstants.SELECT_ID);

                    task.setId(taskId);
                    task.setState(context, ProgramCentralConstants.STATE_PROJECT_TASK_COMPLETE);
                }
            }
        } catch(Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Baseline 검토 -> 완료 Trigger
     * 이전 차수의 Task에 '표준 산출물'이 연결되어 있고 해당 '산출물'이 연결 되어 있으면 개정 차수의 Task에 복사 없으면 이동
     * @param context
     * @param args
     * @throws Exception
     */
	/*public int triggerCopyOrMoveStandardDeliverable(Context context, String[] args) throws Exception {
		String baseLineId = args[0];

		try {
			ATNStandardDeliverable_mxJPO ATNStandardDeliverable = new ATNStandardDeliverable_mxJPO(context,null);
			emxTask_mxJPO emxTask = new emxTask_mxJPO(context,null);

			DomainObject task		= new DomainObject();
			DomainObject deliver	= new DomainObject();

			StringList typeSelects = new StringList(3);
			typeSelects.add(ATNConstants.SELECT_ID);
			typeSelects.add(ATNConstants.SELECT_POLICY);
			typeSelects.add(ATNConstants.SELECT_CURRENT);

			StringList relSelects = new StringList(2);
			relSelects.add(ATNConstants.SELECT_RELATIONSHIP_ID);
			relSelects.add(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);

			DomainObject dmoBaseLine = DomainObject.newInstance(context, baseLineId);

			// 베이스라인에 연결된 'Task' 리스트
			MapList taskList = dmoBaseLine.getRelatedObjects(context, ATNConstants.RELATIONSHIP_ATNBASELINETASK, ATNConstants.TYPE_TASK,
					typeSelects, relSelects, false, true, (short) 1, ATNConstants.EMPTY_STRING, ATNConstants.EMPTY_STRING, 0);

			for(int iTask=0; iTask<taskList.size(); iTask++) {
				Map mTasks				= (Map) taskList.get(iTask);
				String sTaskId			= (String) mTasks.get(ATNConstants.SELECT_ID);
				String sTaskPolicy		= (String) mTasks.get(ATNConstants.SELECT_POLICY);
				String sTaskCurrent		= (String) mTasks.get(ATNConstants.SELECT_CURRENT);
				String sPreTaskRevision	= (String) mTasks.get(ATNConstants.SELECT_ATTRIBUTE_ATNPREVIOUSTASKREVISION);

				if(UIUtil.isNotNullAndNotEmpty(sPreTaskRevision)) {
					String sPreTaskId = ATNDomainUtil.getObjectId(context, ATNConstants.TYPE_TASK, ATNConstants.SYMB_WILD, sPreTaskRevision);

					// 필수산출물 조회
					MapList deliverList	= (MapList) ATNStandardDeliverable.getAllStandardDeliverables(context, sPreTaskId) ;

					// 산출물 조회
					MapList curDeliverables	= (MapList) ATNStandardDeliverable.getTaskDeliverables(context, sPreTaskId) ;

					// 결재 조회
					MapList routeList	= (MapList) ATNStandardDeliverable.getTaskRouteList(context, sPreTaskId);

					for(int iDel=0; iDel<deliverList.size(); iDel++) {
						Map deliverMap			= (Map) deliverList.get(iDel);
						String sDeliverId		= (String) deliverMap.get(DomainConstants.SELECT_ID);
						String sDeliverRelId	= (String) deliverMap.get(ATNConstants.SELECT_RELATIONSHIP_ID);
						String sDeliverRequired	= (String) deliverMap.get("attribute[" + ATNConstants.ATTRIBUTE_ATNSTDREQUIRED + "]");

						boolean checkPassDeliverable = emxTask.checkRequiredDeliverableExist(context, sTaskPolicy, sTaskCurrent, deliverMap, curDeliverables, routeList, false);

						if(!checkPassDeliverable) {
							// 진행중인 Task의 산출물을 제거할 수 없어 Trigger Off 후 진행
							MqlUtil.mqlCommand(context, "trigger off", true, false);
							DomainRelationship.disconnect(context, sDeliverRelId);
							MqlUtil.mqlCommand(context, "trigger on", true, false);
						}

						task.setId(sTaskId);
						deliver.setId(sDeliverId);

						DomainRelationship rel = DomainRelationship.connect(context, task, ATNConstants.RELATIONSHIP_ATNSTANDARDDELIVERABLE, deliver);
						rel.setAttributeValue(context, ATNConstants.ATTRIBUTE_ATNSTDREQUIRED, sDeliverRequired);
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
			throw e;
		}

		return 0;
	}*/

    /**
     * 2020. 03. 31
     * Baseline 승인/반려/회수 시 연결된 문서 상태 변경
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public void setStateBaselineDoc(Context context, String[] args) throws Exception
    {
        String objectId = args[0];
        try
        {
            DomainObject dmo = new DomainObject();
            dmo.setId(objectId);
            String sBaselineCurrent = dmo.getInfo(context, ATNConstants.SELECT_CURRENT);

            String setDocState = "";
            if(ATNConstants.STATE_ATNBASELINELOG_CREATE.equals(sBaselineCurrent)) {
                setDocState = ATNConstants.STATE_ATNDOCUMENTS_INWORK;
            } else if(ATNConstants.STATE_ATNBASELINELOG_REVIEW.equals(sBaselineCurrent)) {
                setDocState = ATNConstants.STATE_ATNDOCUMENTS_INREVIEW;
            } else if(ATNConstants.STATE_ATNBASELINELOG_APPROVE.equals(sBaselineCurrent)) {
                setDocState = ATNConstants.STATE_ATNDOCUMENTS_APPROVAL;
            } else if(ATNConstants.STATE_ATNBASELINELOG_COMPLETE.equals(sBaselineCurrent)) {
                setDocState = ATNConstants.STATE_ATNDOCUMENTS_RELEASED;
            }

            MapList mlBaselineDoc = getATNBaselineDoc(context, objectId);

            if(!mlBaselineDoc.isEmpty() && mlBaselineDoc.size() > 0 && UIUtil.isNotNullAndNotEmpty(setDocState))
            {
                Map mBaselineDoc 	  = (Map) mlBaselineDoc.get(0);
                String sBaselineDocId = (String) mBaselineDoc.get(DomainConstants.SELECT_ID);
                dmo.setId(sBaselineDocId);
                dmo.setState(context, setDocState);
            }
        }
        catch(Exception e)
        {
            e.printStackTrace();
        }
    }

    /**
     * 2020. 03. 31
     * 베이스라인에 연결 된 문서 조회
     * @param context the eMatrix <code>Context</code> object
     * @param objectId
     * @param where
     * @param sort
     * @return MapList
     * @throws Exception if the operation fails
     */
    public MapList getATNBaselineDoc(Context context, String objectId) throws Exception
    {
        MapList mlBaselineDoc = new MapList();

        try
        {
            StringList typeSelects = new StringList(4);
            typeSelects.addElement(DomainObject.SELECT_ID);
            typeSelects.addElement(DomainObject.SELECT_CURRENT);

            DomainObject dmo = new DomainObject(objectId);

            mlBaselineDoc    = dmo.getRelatedObjects(context,
                    ATNConstants.RELATIONSHIP_ATNBASELINEDOC,   //relationship Name
                    ATNConstants.TYPE_ATNDOCUMENTS, 			// Type Name
                    typeSelects,           				 		// object selects  : 최종 개체로부터 추출할 속성값들
                    null,     					   	    		// relationship selects  : 릴레이션의속성값들
                    false,               			    		// from   direction
                    true,          			        			// to direction
                    (short) 0,        		        			// recursion level  : 관계의 수준
                    null,										// object where clause : 최종개체추출의 where절(mql )
                    null) ;
        }
        catch(Exception e)
        {
            e.printStackTrace();
            throw e;
        }
        return mlBaselineDoc;
    }

    private void setProjectLeadModifyAccess(Context context, String projectId, String objectId) throws Exception
    {
        try
        {
            ContextUtil.startTransaction(context, true);
            String sUserName = context.getUser();

            BusinessObjectList objects = new BusinessObjectList();
            ProjectSpace project = new ProjectSpace();
            project.setId(projectId);

            StringList selBUS = new StringList();
            selBUS.add(ATNConstants.SELECT_NAME);
            selBUS.add(ATNConstants.SELECT_ID);
            selBUS.add(DomainConstants.SELECT_HAS_MODIFY_ACCESS);

            StringList selREL = new StringList();
            selREL.add(MemberRelationship.SELECT_PROJECT_ACCESS);
            selREL.add(MemberRelationship.SELECT_PROJECT_ROLE);

            MapList mlProjectMember = project.getMembers(context, selBUS, selREL, null, null);

            Iterator memberItr = mlProjectMember.iterator();
            while(memberItr.hasNext())
            {
                Map mCurrentMember = (Map) memberItr.next();
                String sName   		   = (String) mCurrentMember.get(ATNConstants.SELECT_NAME);
                String sAccess 		   = (String) mCurrentMember.get(MemberRelationship.SELECT_PROJECT_ROLE);
                String hasModifyAccess = (String) mCurrentMember.get(DomainConstants.SELECT_HAS_MODIFY_ACCESS);

                if(sUserName.equals(sName) && sAccess.equals("Project Lead") && !hasModifyAccess.equalsIgnoreCase("TRUE"))
                {
                    objects.addElement(new BusinessObject(objectId));
                    Access access = new Access();
                    access.setModifyAccess(true);
                    access.setUser(sUserName);

                    ContextUtil.pushContext(context);
                    DomainObject.grantAccessRights(context, objects, access);
                    ContextUtil.popContext(context);
                }
            }
            ContextUtil.commitTransaction(context);
        } catch (Exception ex) {
            ContextUtil.abortTransaction(context);
            throw new FrameworkException(ex.getMessage());
        }
    }
}
