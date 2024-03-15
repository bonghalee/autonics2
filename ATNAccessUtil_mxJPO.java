import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import matrix.db.Access;
import matrix.db.BusinessObject;
import matrix.db.Context;
import matrix.db.JPO;
import matrix.db.Role;
import matrix.db.User;
import matrix.db.UserList;
import matrix.util.Pattern;
import matrix.util.StringList;

import com.atn.apps.common.util.ATNConstants;
import com.atn.apps.common.util.ATNStringUtil;
import com.matrixone.apps.domain.DomainConstants;
import com.matrixone.apps.domain.DomainObject;
import com.matrixone.apps.domain.util.EnoviaResourceBundle;
import com.matrixone.apps.domain.util.FrameworkException;
import com.matrixone.apps.domain.util.FrameworkUtil;
import com.matrixone.apps.domain.util.MapList;
import com.matrixone.apps.domain.util.MqlUtil;
import com.matrixone.apps.domain.util.PropertyUtil;
import com.matrixone.apps.framework.ui.UIUtil;
import com.matrixone.apps.program.ProgramCentralConstants;


/**
 *<PRE>
 * Name : ATNAccessUtil
 * DESC : 접근제어 유틸
 * VER. : 1.0
 * AUTHOR : jkpark
 * PROJ : ATN PLM
 *
 * Copyright 2015 by ENOVIA All rights reserved.
 *----------------------------------------------------------------------------
 *                Revision history
 *----------------------------------------------------------------------------
 *  DATE          Author                    Description
 * ----------   ---------------------      -------------------------------------
 * 11/17/2015     jkpark                    First Creation
 * </PRE>

 * @author jkpark
 * @version   1.0 11/13/2015
 */
public class ATNAccessUtil_mxJPO {

    private static final String SELECT_FROM_PROJECT_ACCESS_TYPE = "to[" + DomainConstants.RELATIONSHIP_PROJECT_ACCESS_KEY +
            "].from.from[" + DomainConstants.RELATIONSHIP_PROJECT_ACCESS_LIST + "].to." + DomainConstants.SELECT_TYPE;
    private static final String SELECT_FROM_PROJECT_ACCESS_CURRENT = "to[" + DomainConstants.RELATIONSHIP_PROJECT_ACCESS_KEY +
            "].from.from[" + DomainConstants.RELATIONSHIP_PROJECT_ACCESS_LIST + "].to." + DomainConstants.SELECT_CURRENT;

    public boolean checkAccess( Context context , String[] args) throws Exception
    {
        boolean access = false ;
        HashMap programMap = (HashMap) JPO.unpackArgs(args);
        //Getting the Settings of Command
        Map settingMap = (HashMap) programMap.get("SETTINGS");

        String AccessType = (String)settingMap.get("Access Type");
        String objectId   = (String)programMap.get("objectId");


        //System.out.println(AccessType);
        //System.out.println(objectId);

        if(AccessType.equals("isPM")){
            access = checkProjectPM(context,objectId);
        }else if(AccessType.equals("isAssignee")){
            access = checkTaskAssignee(context,objectId);
        }else if(AccessType.equals("TaskStart")){
            access = checkTaskStart(context,objectId);
        }else if(AccessType.equals("TaskFinish")){
            access = checkTaskFinish(context,objectId);
        }else if(AccessType.equals("TaskModify")){
            access = checkTaskModify(context,objectId);
        }else if(AccessType.equals("reviseBaseline")){
            access = checkAccessReviseBaseline(context,objectId);
        }else if(AccessType.equals("viewChecklist")){
            access = checkAccessViewChecklist(context,objectId);
        }else if(AccessType.equals("checklistPromote")){
            access = checkAccessChecklistPromotable( context , objectId );
        }else if(AccessType.equals("deliverableRemove")){
            access = checkAccessDeliverableRemove( context , objectId );
        }else if(AccessType.equals("taskRequestApprove")){
            access = checkAccessTaskRequestApprove( context , objectId );
        }else if(AccessType.equals("isApproveEnabled")){
            access = checkAccessGateRequestApprove( context , objectId , args );
        }else if(AccessType.equals("isReactivateEnabled")){
            access = checkAccessGateRequestApprove( context , objectId , args );
        }else if(AccessType.equals("isHoldEnabled")){
            access = checkAccessGateRequestApprove( context , objectId , args );
        }else if(AccessType.equals("isCancelEnabled")){
            access = checkAccessGateRequestApprove( context , objectId , args );
        }else if(AccessType.equals("weeklyTimesheetInterface")){
            access = checkAccessWeeklyTimesheetInterface( context , "ATNInterfaceAdmin" );
        }else if(AccessType.equals("TaskAdd")){
            access = checkTaskAdd(context,objectId);
        }
        /**/
        else if(AccessType.equals("hasCloneAccess")){
            access = hasCloneAccess(context,objectId);
        }

        return access ;
    }
    /**
     * 프로젝트의 PM권한을 체크한다.
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public static boolean checkProjectPM(Context context, String objectId ) throws Exception
    {
        boolean access = false ;

        DomainObject curObject = new DomainObject(objectId);
        String projectId = null  ;

        String curType = curObject.getInfo(context,"type");

        // 프로젝트 작성자체크 변수
        boolean projectOwnerMatch = false ;

        if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_SPACE)){

            projectId = objectId ;

        }else if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_TEMPLATE)){

            if(curObject.getInfo(context, "owner").equals(context.getUser())) return true ;

        }else if(
                curType.equals( ProgramCentralConstants.TYPE_PHASE)||
                        curType.equals( ProgramCentralConstants.TYPE_GATE)||
                        curType.equals( ProgramCentralConstants.TYPE_TASK)||
                        curType.equals( ProgramCentralConstants.TYPE_MILESTONE)
        ){

            com.matrixone.apps.common.Task task = new com.matrixone.apps.common.Task();
            task.newInstance(context);
            task.setId(objectId);
            DomainObject dmoProject = task.getProjectObject(context);
            projectId = dmoProject.getId();

        }else if(curType.equals( ATNConstants.TYPE_ATNBASELINELOG)){

            projectId = curObject.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_BASELINE_LOG + "].from.id" );

        }
        DomainObject project = DomainObject.newInstance(context, projectId);

        String projectOwner = project.getInfo(context,"owner");
        if(projectOwner.equals(context.getUser())) projectOwnerMatch = true ;


        if(project.getInfo(context, "type").equals(ProgramCentralConstants.TYPE_PROJECT_TEMPLATE)){
            if(project.getInfo(context, "owner").equals(context.getUser())) return true ;
        }

        StringList slBusSelect = new StringList();
        slBusSelect.addElement(DomainConstants.SELECT_ID);
        slBusSelect.addElement(DomainConstants.SELECT_NAME);
        slBusSelect.addElement("attribute["+ProgramCentralConstants.ATTRIBUTE_PROJECT_ROLE+"]");

        StringList slRelSelect = new StringList();

        MapList mlMemberList = project.getRelatedObjects(context
                ,ProgramCentralConstants.RELATIONSHIP_MEMBER   // relationship pattern
                ,ProgramCentralConstants.TYPE_PERSON                      // object pattern
                ,slBusSelect                           // object selects
                ,slRelSelect                           // relationship selects
                ,false                             // to direction
                ,true                              // from direction
                ,(short) 0                         // recursion level
                ," name == '"+context.getUser()+"' "                                // object where clause
                ," attribute["+ProgramCentralConstants.ATTRIBUTE_PROJECT_ROLE+"].value == 'Project Lead' "                                // relationship where clause
                ,0);

//        if(mlMemberList.size()>0 && projectOwnerMatch){
        if(mlMemberList.size()>0){
            access = true ;
        }
        /* 2018-01-16
         * admin은 PM check에서 자유로움
         */
        if("admin_platform".equalsIgnoreCase(context.getUser())){
            access = true ;
        }
        return access ;
    }


    /**
     * 담당자 권한을 체크한다.
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkTaskAssignee(Context context, String objectId ) throws Exception
    {
        boolean access = false ;

        DomainObject curObject = new DomainObject(objectId);
        String projectId = null  ;

        String curType = curObject.getInfo(context,"type");


        if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_SPACE)){

            return false ;

        }else if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_TEMPLATE)){

            return false ;

        }else if(
                curType.equals( ProgramCentralConstants.TYPE_PHASE)||
                        curType.equals( ProgramCentralConstants.TYPE_GATE)||
                        curType.equals( ProgramCentralConstants.TYPE_TASK)||
                        curType.equals( ProgramCentralConstants.TYPE_MILESTONE)
        ){

            com.matrixone.apps.common.Task task = new com.matrixone.apps.common.Task();
            task.newInstance(context);
            task.setId(objectId);

            StringList slBusSelect = new StringList();
            slBusSelect.addElement(DomainConstants.SELECT_ID);
            slBusSelect.addElement(DomainConstants.SELECT_NAME);
            slBusSelect.addElement("attribute["+ProgramCentralConstants.ATTRIBUTE_PROJECT_ROLE+"]");

            StringList slRelSelect = new StringList();

            MapList mlMemberList = task.getRelatedObjects(context
                    ,ProgramCentralConstants.RELATIONSHIP_ASSIGNED_TASKS   // relationship pattern
                    ,ProgramCentralConstants.TYPE_PERSON                      // object pattern
                    ,slBusSelect                           // object selects
                    ,slRelSelect                           // relationship selects
                    ,true                             // to direction
                    ,false                              // from direction
                    ,(short) 0                         // recursion level
                    ," name == '"+context.getUser()+"' "                                // object where clause
                    ,""                                // relationship where clause
                    ,0);

            if(mlMemberList.size()>0) access = true ;
        }



        return access ;
    }


    /**
     * 타스크가 시작가능한지 체크
     * 조건 추가 타스크의 프로젝트 상태가 진행중일때만 허용한다.
     * 프로젝트상태가 계획중일때 프로젝트 승인 결재가 필요하므로 타스크 상태를 시작할수 없다.
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkTaskStart(Context context, String objectId ) throws Exception
    {
        boolean access = false ;

        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        String current = curObject.getInfo(context,"current");


        if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_SPACE)){

            return false ;

        }else if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_TEMPLATE)){

            return false ;

        }else if(
//        		curType.equals( ProgramCentralConstants.TYPE_PHASE)||
                curType.equals( ProgramCentralConstants.TYPE_TASK)||
                        curType.equals( ProgramCentralConstants.TYPE_MILESTONE)
        ){

            com.matrixone.apps.program.Task task = (com.matrixone.apps.program.Task) DomainObject.newInstance(context,
                    DomainConstants.TYPE_TASK, "PROGRAM");

            task.setId(objectId);
            StringList busSelects = new StringList(5);
            busSelects.add(DomainConstants.SELECT_ID);
            busSelects.add("current");
            busSelects.add("attribute[ATNProjectType]");
            Map projectInfo = task.getProject(context, busSelects);
            String pObjectId	= (String) projectInfo.get(DomainConstants.SELECT_ID);
            String pCurrent    	= (String) projectInfo.get("current");
            String pType    	= (String) projectInfo.get("attribute[ATNProjectType]");

            if(!checkBaseline(context, objectId))
            {
                return false;
            }

            if( pCurrent.equals("Active") && current.equals( ProgramCentralConstants.STATE_PROJECT_TASK_ASSIGN )){
                access = checkTaskAssignee(context,objectId);
            }else if(pCurrent.equals("Assign") && ( pType.equals("Mold")|| pType.equals("ETC")) && current.equals( ProgramCentralConstants.STATE_PROJECT_TASK_ASSIGN )){
                access = checkTaskAssignee(context,objectId);
            }
        }else if(
                curType.equals( ProgramCentralConstants.TYPE_GATE)){

            com.matrixone.apps.program.Task task = (com.matrixone.apps.program.Task) DomainObject.newInstance(context,
                    DomainConstants.TYPE_TASK, "PROGRAM");

            task.setId(objectId);
            StringList busSelects = new StringList(4);
            busSelects.add("current");
            Map projectInfo = task.getProject(context, busSelects);
            String pCurrent    = (String) projectInfo.get("current");

            if( pCurrent.equals("Active") && current.equals( ProgramCentralConstants.STATE_PROJECT_REVIEW_CREATE )){
                access = checkTaskAssignee(context,objectId);
            }
        }



        return access ;
    }

    /**
     * 타스크가 완료가능한지 체크
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkTaskFinish(Context context, String objectId ) throws Exception
    {
        boolean access = false ;

        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        String current = curObject.getInfo(context,"current");


        if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_SPACE)){

            return false ;

        }else if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_TEMPLATE)){

            return false ;

        }else if(
//        		curType.equals( ProgramCentralConstants.TYPE_PHASE)||
                curType.equals( ProgramCentralConstants.TYPE_TASK)||
                        curType.equals( ProgramCentralConstants.TYPE_MILESTONE)
        ){

            if(!checkBaseline(context, objectId))
            {
                return false;
            }

            if( current.equals( ProgramCentralConstants.STATE_PROJECT_TASK_ACTIVE ) || current.equals( ProgramCentralConstants.STATE_PROJECT_TASK_REVIEW )){
                String sDevCompleteFlag = curObject.getAttributeValue(context, ATNConstants.ATTRIBUTE_ATNDEVCOMPLETEFLAG);
                boolean bFlag = Boolean.valueOf(sDevCompleteFlag);

                access = checkTaskAssignee(context,objectId) && !bFlag;
            }
        }else if(
                curType.equals( ProgramCentralConstants.TYPE_GATE)){

//        	if( current.equals( ProgramCentralConstants.STATE_PROJECT_REVIEW_REVIEW )){
//        		access = checkTaskAssignee(context,objectId);
//        	}
        }



        return access ;
    }

    /**
     * 프로젝트가 베이스라인 수정이 가능한지 체크
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkAccessReviseBaseline(Context context, String objectId ) throws Exception
    {
        boolean access = false ;

        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        String current = curObject.getInfo(context,"current");


        if(curType.equals(ProgramCentralConstants.TYPE_PROJECT_SPACE)){
            if(current.equals(ProgramCentralConstants.STATE_PROJECT_TASK_ACTIVE)){
                if(checkProjectPM(context , objectId)){
                    // 진행단계에서 PM일때
                    ATNBaseline_mxJPO jpo = new ATNBaseline_mxJPO(context ,null);
                    HashMap programMap = new HashMap();
                    programMap.put("objectId", objectId);

                    MapList list = jpo.getATNBaseline(context, JPO.packArgs(programMap));
                    // 베이스라인이 존재할 경우만 리바이즈 가능하다
                    if(list.size()>0){

                        for(int iBase=0; iBase<list.size(); iBase++){
                            Map mBaseline = (Map) list.get(iBase);
                            String strBaselineCurrent = (String) mBaseline.get(DomainConstants.SELECT_CURRENT);

                            if(!ATNConstants.STATE_ATNBASELINELOG_COMPLETE.equals(strBaselineCurrent) &&
                                    !"Exists".equals(strBaselineCurrent))
                                return false;
                        }

                        return true ;
                    }

                }
            }

        }



        return access ;
    }


    /**
     * 게이트의 체크리스트 조회 권한 체크
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkAccessViewChecklist(Context context, String objectId ) throws Exception
    {
        boolean access = false ;

        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");


        if(curType.equals( ProgramCentralConstants.TYPE_GATE)){
            //com.matrixone.apps.common.Task task = new com.matrixone.apps.common.Task();
            //task.newInstance(context);
            //task.setId(objectId);
            //DomainObject dmoProject = task.getProjectObject(context);
            //String projectType = dmoProject.getInfo(context, DomainConstants.SELECT_POLICY);

            //if(projectType.equals(ProgramCentralConstants.TYPE_PROJECT_TEMPLATE)){
            access = true ;
            //}

        }



        return access ;
    }


    /**
     *
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    private boolean checkAccessChecklistPromotable( Context context , String objectId )
            throws Exception
    {
        boolean access = false ;
        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        //System.out.println(curObject);

        if(curType.equals( ProgramCentralConstants.TYPE_GATE)){
            StringList slBusSelect = new StringList();
            slBusSelect.addElement(DomainConstants.SELECT_ID);
            slBusSelect.addElement(DomainConstants.SELECT_NAME);

            StringList slRelSelect = new StringList();

            MapList mlactivecheckList = curObject.getRelatedObjects(context
                    ,"Checklist"   // relationship pattern
                    ,"Checklist"                      // object pattern
                    ,slBusSelect                           // object selects
                    ,slRelSelect                           // relationship selects
                    ,false                             // to direction
                    ,true                              // from direction
                    ,(short) 1                         // recursion level
                    ," current == 'Active' "                                // object where clause
                    ,""                                // relationship where clause
                    ,0);

            // 진행중인 체크리스트가 있다면 완료할수 있다
            if(mlactivecheckList.size()>0){
                // PM일때 체크리스트 완료가능하다.
                if(checkProjectPM(context, objectId)){
                    //System.out.println("PM OK");
                    access = true ;
                }
            }

        }
        return access ;
    }


    /**
     * 산출물에서 제거
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    private boolean checkAccessDeliverableRemove( Context context , String objectId )
            throws Exception
    {
        boolean access = false ;
        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        String current = curObject.getInfo(context,"current");
//System.out.println(curType+","+current);
        //admin 전체 허용
        if("admin_platform".equals(context.getUser())){
            access = true;
        }else{

            if(curType.equals( ProgramCentralConstants.TYPE_GATE)){

                if( current.equals("Create")) access = true ;

            }else if(curType.equals( ProgramCentralConstants.TYPE_TASK) || curType.equals( ProgramCentralConstants.TYPE_PHASE) || curType.equals( ProgramCentralConstants.TYPE_MILESTONE)){
                if( current.equals("Create") ||  current.equals("Assign") ||  current.equals("Active")) access = true ;
            }
        }
        return access ;
    }


    /**
     * [artf1217]
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    private boolean checkAccessTaskRequestApprove( Context context , String objectId )
            throws Exception
    {
        boolean access = false ;
        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        String current = curObject.getInfo(context,"current");
//		System.out.println(curType+","+current);
        // 게이트 결재는 다른 커맨드에서 권한 체크하므로 여기에서 게이트는 항상  false 리턴한다.
    	/*if(curType.equals( ProgramCentralConstants.TYPE_GATE)){

        	if( current.equals("Create")) access = true ;

        }else*/
        /**
         * 2019.05.16
         * phase 결재 상신 하위 일정들이 전부 완료 되면 가능하도록 로직 변경
         */
//    	if(curType.equals( ProgramCentralConstants.TYPE_TASK) || curType.equals( ProgramCentralConstants.TYPE_PHASE) || curType.equals( ProgramCentralConstants.TYPE_MILESTONE)){
        if(curType.equals( ProgramCentralConstants.TYPE_TASK) || curType.equals( ProgramCentralConstants.TYPE_MILESTONE)){
            // 진행중이거나 리뷰단계에서 결재 상신 , 결재 재상신 모두 가능하여야 한다.

            if(!checkBaseline(context, objectId))
            {
                return false;
            }

            if( current.equals("Active") || current.equals("Review")) access = true ;

            // 개발완료 Task일 때, 프로젝트에 '개발완료보고서(문서)'가 연결되어 있지 않으면 상신할 수 없음
            String sDevCompleteFlag = curObject.getAttributeValue(context, ATNConstants.ATTRIBUTE_ATNDEVCOMPLETEFLAG);
            if("True".equalsIgnoreCase(sDevCompleteFlag)) {
                String sProjectId = curObject.getInfo(context, "to[" + DomainObject.RELATIONSHIP_SUBTASK + "].from.id");
                DomainObject project = new DomainObject(sProjectId);

                MapList docList = project.getRelatedObjects(context, ATNConstants.RELATIONSHIP_ATNPJTREQUIREDDOC, ATNConstants.TYPE_ATNDOCUMENTS
                        , DomainConstants.EMPTY_STRINGLIST, DomainConstants.EMPTY_STRINGLIST, false, true, (short) 0, DomainConstants.EMPTY_STRING
                        , ATNConstants.SELECT_ATTRIBUTE_ATNPJTREQUIREDSTATE + " == Active", 0);

                if(docList.size() < 1)
                    return false;
            }

        }else if(curType.equals( ProgramCentralConstants.TYPE_PHASE)){

            StringList slBusSelect = new StringList();
            slBusSelect.addElement(DomainConstants.SELECT_ID);
            slBusSelect.addElement(DomainConstants.SELECT_NAME);

            StringList slRelSelect = new StringList();

            MapList taskList = curObject.getRelatedObjects(context
                    ,ProgramCentralConstants.RELATIONSHIP_SUBTASK                 // relationship pattern
                    ,ProgramCentralConstants.TYPE_TASK                            // object pattern
                    ,slBusSelect                                                  // object selects
                    ,slRelSelect                                                  // relationship selects
                    ,false                                                        // to direction
                    ,true                                                         // from direction
                    ,(short) 10                                                   // recursion level
                    ," current != 'Complete' "                                    // object where clause
                    ,""                                                           // relationship where clause
                    ,0);
            /* 2019.05.16
             * 완료되지 않은 Task가 있으면 비활성화
             */
            if(taskList.size()>0){
                access = false;
            }else{
                access = true ;
            }
        }
        return access ;
    }


    /**
     * [artf1254]
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    private boolean checkAccessGateRequestApprove( Context context , String objectId , String[] args )
            throws Exception
    {
        boolean access = false ;

        HashMap programMap = (HashMap) JPO.unpackArgs(args);
        //Getting the Settings of Command
        Map settingMap = (HashMap) programMap.get("SETTINGS");

        String AccessType = (String)settingMap.get("Access Type");

        DomainObject curObject = new DomainObject(objectId);

        String curType = curObject.getInfo(context,"type");
        String current = curObject.getInfo(context,"current");
        String ATNDecision = curObject.getInfo(context,"attribute[ATNDecision]");

        // OOTB check logic
        String program = "emxProjectHoldAndCancel";
        String method = AccessType ;

        access = (Boolean)JPO.invoke( context, program, null, method, args , Boolean.class ).booleanValue() ;

        System.out.println(curType+","+current+","+access+","+ATNDecision+","+method);

        return access ;
    }



    /**
     * [artf1236]
     * @param context
     * @param roleName
     * @return
     * @throws Exception
     */
    private boolean checkAccessWeeklyTimesheetInterface( Context context , String roleName )
            throws Exception
    {
        boolean access = false ;
        try {
            Role role = new Role(roleName);
            UserList users= role.getAssignments(context);

            int num = users.size();

            for(int i = 0; i < num; i++){
                User userManager = (User)users.get(i);
                if(userManager.getName().equals(context.getUser())){
                    access = true;
                    break;
                }
            }
        } catch (Exception ex) {
            access = false;
            throw new FrameworkException((String) ex.getMessage());
        }
        return access ;
    }

    public boolean checkResearch(Context context, String[] args)throws Exception{
        boolean result = true;

        try{
            boolean isContainPerson = isAssigned(context, context.getUser(), "ATNResearchManager");
            if(isContainPerson)
                result = false;

        }catch(Exception e){
            e.printStackTrace();
        }

        return result;
    }

    public boolean checkOnlyResearch(Context context, String[] args)throws Exception{
        boolean result = true;

        try{
            result = isAssigned(context, context.getUser(), "ATNResearchManager");

        }catch(Exception e){
            e.printStackTrace();
        }

        return result;
    }

    private static boolean isAssigned(Context context, String personName, String roleName) throws Exception {
        String strMQL = "print person  '" + personName + "' select isassigned[" + roleName + "] dump";
        boolean isAssigned = "true".equalsIgnoreCase(MqlUtil.mqlCommand(context, strMQL, true)) ? true : false;

        return isAssigned;
    }

    /**
     * Part와 연결된 NPR이 있는지 체크
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public boolean checkPartEditCommand(Context context, String[] args)throws Exception{

        boolean bCheck = true;

        HashMap programMap = (HashMap) JPO.unpackArgs(args);
        String objectId   = (String)programMap.get("objectId");

        if(!context.isAssigned("Administration Manager")) {

            DomainObject partDmo = new DomainObject(objectId);

            MapList mlNewPartRequest = partDmo.getRelatedObjects(context
                    ,DomainConstants.RELATIONSHIP_AFFECTED_ITEM   	// relationship pattern
                    ,ATNConstants.TYPE_ATNNEWPARTREQUEST          	// object pattern
                    ,new StringList()                      		  	// object selects
                    ,new StringList()                           	// relationship selects
                    ,true                             				// to direction
                    ,false                             				// from direction
                    ,(short) 0                         				// recursion level
                    ,""												// object where clause
                    ,""				                               	// relationship where clause
                    ,0);

            if(mlNewPartRequest.size() > 0) {
                bCheck = false;
            }
        }

        return bCheck;
    }
    /**
     * 소유권 이관 Cmd 활성화 여부
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public boolean checkTransferOwnership(Context context, String[] args) throws Exception
    {
        HashMap programMap = (HashMap) JPO.unpackArgs(args);
        Map settingMap = (HashMap) programMap.get("SETTINGS");
        String objectId		= (String)programMap.get("objectId");
        String transType	= (String)settingMap.get("TransType");
        String contextUser	= context.getUser();
        boolean bCheck = false;

        DomainObject dmObj = new DomainObject(objectId);

        StringList selects = new StringList(5);
        selects.add(DomainConstants.SELECT_OWNER);
        selects.add(DomainConstants.SELECT_TYPE);
        selects.add(DomainConstants.SELECT_POLICY);
        selects.add(DomainConstants.SELECT_CURRENT);
        selects.add(ATNConstants.SELECT_ATTRIBUTE_ATNDRAWINGTYPE);

        Map mInfo = dmObj.getInfo(context, selects);
        String dmObjOwner	= (String) mInfo.get(DomainConstants.SELECT_OWNER);
        String dmObjType	= (String) mInfo.get(DomainConstants.SELECT_TYPE);
        String dmObjPolicy	= (String) mInfo.get(DomainConstants.SELECT_POLICY);
        String currentState	= (String) mInfo.get(DomainConstants.SELECT_CURRENT);
        String drwType		= (String) mInfo.get(ATNConstants.SELECT_ATTRIBUTE_ATNDRAWINGTYPE);

        // Owner 체크
        boolean bCheckOwner = contextUser.equals(dmObjOwner);

        // 도면 관리자 권한 체크
        boolean bDrwAdmin = checkCADDrawingAdmin(context, contextUser, dmObjPolicy) || bCheckOwner;

        //"Fetch"
        //"Move"
        /**
         * 가져 오는 기능 일경우
         * Object가 품목,S/S 도면 이면서 Owner가 admin_platform일경우 가능
         */
        if("Fetch".equals(transType)){
            //현재 접속 사용자 체크
            if("admin_platform".equals(contextUser)){
                bCheck = true;
            }else{
                if("admin_platform".equals(dmObjOwner)){
                    if("ATNPart".contains(dmObjType)||"ATNProductPart".contains(dmObjType)){
                        bCheck = true;
                    }else if("CAD Drawing".contains(dmObjType)
                        /*&& bDrwAdmin*/
                    ){
                        //01 기구도면
                        //02 PCB도면
                        //03 회로도면
//        				drwType=dmObj.getInfo(context, "ATNDrawingType");

                        //bCheck="02".equals(drwType)||"03".equals(drwType);
                        bCheck = true;
                    }
                }
            }
        }else if("Move".equals(transType)){
            //현재 접속 사용자 체크
            if("admin_platform".equals(contextUser)){
                bCheck = true;
            }else{
                if("ATNPart".contains(dmObjType)||"ATNProductPart".contains(dmObjType)){
                    // 파트 관리자 또는 생성자 권한 체크
                    boolean bPartAdmin = isAssigned(context, contextUser, ATNConstants.ROLE_ATNPARTADMIN);

                    bCheck = "Preliminary".equals(currentState) && (bCheckOwner || bPartAdmin);
                }else if("CAD Drawing".contains(dmObjType) && bDrwAdmin){
                    //01 기구도면
                    //02 PCB도면
                    //03 회로도면
//        			drwType=dmObj.getInfo(context, "ATNDrawingType");
                    bCheck = "Preliminary".equals(currentState)&&"01".equals(drwType);
                }else if("ATNDocuments".contains(dmObjType)){
                    // 문서 관리자 권한 체크
                    boolean bDocAdmin = isAssigned(context, contextUser, ATNConstants.ROLE_ATNDOCUMENTSADMIN);

                    bCheck = "In work".equals(currentState) && (bCheckOwner || bDocAdmin);
                }
            }
        }
        return bCheck;
    }
    /**
     * 개정 Cmd 활성화 가능 여부
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public boolean checkRevisePossible(Context context, String[] args)throws Exception{
        boolean bCheck = false;
        boolean bCheckRole = false;
        HashMap programMap = (HashMap) JPO.unpackArgs(args);
        String objectId   = (String)programMap.get("objectId");

        StringList selects = new StringList(3);
        selects.add(DomainConstants.SELECT_TYPE);
        selects.add(DomainConstants.SELECT_OWNER);
        selects.add(DomainConstants.SELECT_CURRENT);

        DomainObject dmObj = new DomainObject(objectId);
        Map mInfo = dmObj.getInfo(context, selects);

        //Obj Type
        String dmObjType = (String) mInfo.get(DomainConstants.SELECT_TYPE);
        //Obj 소유자
        String dmObjOwner = (String) mInfo.get(DomainConstants.SELECT_OWNER);
        //Obj 현재 상태
        String currentState = (String) mInfo.get(DomainConstants.SELECT_CURRENT);
        String drwType = null;
        //현재 접속 사용자
        String contextUser = context.getUser();

        //
        bCheckRole = isAssigned(context, contextUser, ATNConstants.ROLE_ATNDSNDRAWINGADMIN) ||
                isAssigned(context, contextUser, ATNConstants.ROLE_ATNDSNDRAWINGDESIGNEER) ||
                isAssigned(context, contextUser, ATNConstants.ROLE_ATNDRWDRAWINGDESIGNEER) ||
                isAssigned(context, contextUser, ATNConstants.ROLE_ATNHADDRAWINGDESIGNEER)
        ;

        //현재 접속 사용자 체크
        if("admin_platform".equals(contextUser)){
            bCheck = true;
        }else{
            if("ATNPart".contains(dmObjType)||"ATNProductPart".contains(dmObjType)){

            }else if("CAD Drawing".contains(dmObjType)){

                if(DomainConstants.STATE_CADDRAWING_RELEASE.equals(currentState) && (bCheckRole || dmObjOwner.equals(contextUser)))
                {
                    drwType=dmObj.getInfo(context, "ATNDrawingType");

                    String program = "emxENCActionLinkAccess";
                    String method = "isTypeAccessable" ;
                    bCheck =  (Boolean)JPO.invoke( context, program, null, method, args , Boolean.class ).booleanValue()
                            &&!("02".equals(drwType)||"03".equals(drwType));
                }
            }else if("ATNDocuments".contains(dmObjType)){

            }
        }

        return bCheck;
    }

    /**
     * 타스크 수정 권한 체크
     * @param context
     * @param objectId
     * @return access
     * @throws Exception
     */
    public boolean checkTaskModify(Context context, String objectId) throws Exception {
        boolean access = false;

        StringList selects = new StringList(4);
        selects.add(ATNConstants.SELECT_CURRENT);
        selects.add(ATNConstants.SELECT_ATTRIBUTE_ATNBEFORBASELINETASKFLAG);
        selects.add(SELECT_FROM_PROJECT_ACCESS_TYPE);
        selects.add(SELECT_FROM_PROJECT_ACCESS_CURRENT);

        DomainObject dmo = new DomainObject(objectId);
        Map objectMap = dmo.getInfo(context, selects);

        String strCurrent				= (String) objectMap.get(ATNConstants.SELECT_CURRENT);
        String strBeforBaselineTaskFlag	= (String) objectMap.get(ATNConstants.SELECT_ATTRIBUTE_ATNBEFORBASELINETASKFLAG);
        String strProjectAccessType		= (String) objectMap.get(SELECT_FROM_PROJECT_ACCESS_TYPE);
        String strProjectAccessCurrent	= (String) objectMap.get(SELECT_FROM_PROJECT_ACCESS_CURRENT);

        if(ATNConstants.TYPE_PROJECT_SPACE.equals(strProjectAccessType)){
            if(ProgramCentralConstants.STATE_PROJECT_SPACE_CREATE.equals(strProjectAccessCurrent)) {
                if(ProgramCentralConstants.STATE_PROJECT_TASK_CREATE.equals(strCurrent) || ProgramCentralConstants.STATE_PROJECT_TASK_ASSIGN.equals(strCurrent) ||
                        ProgramCentralConstants.STATE_PROJECT_REVIEW_CREATE.equals(strCurrent))
                {
                    access = true;
                }
            } else if(ProgramCentralConstants.STATE_PROJECT_SPACE_ASSIGN.equals(strProjectAccessCurrent) || "admin_platform".equals(context.getUser())) {
                access = true;
            }
        } else if(ATNConstants.TYPE_PROJECT_TEMPLATE.equals(strProjectAccessType)) {
            return true;
        }

        if("Y".equalsIgnoreCase(strBeforBaselineTaskFlag)){
            access = false;
        }

        if(!checkBaseline(context, objectId)) {
            return false;
        }

        return access ;
    }

    /**
     * 베이스라인의 연결 관계와 상태에 따라 권한 부여
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkBaseline(Context context, String objectId) throws Exception
    {
        boolean blAccess = true;
        StringList typeSelects = new StringList();

        try
        {
            DomainObject dmoTask = new DomainObject(objectId);

            typeSelects = new StringList(4);
            typeSelects.addElement(DomainConstants.SELECT_NAME);
            typeSelects.addElement("to[" + ATNConstants.RELATIONSHIP_PROJECT_ACCESS_KEY + "].from.from[" +
                    ATNConstants.RELATIONSHIP_PROJECT_ACCESS_LIST + "].to.id");
            typeSelects.addElement("to[" + ATNConstants.RELATIONSHIP_ATNBASELINETASK + "].from." +
                    ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);
            typeSelects.addElement("to[" + ATNConstants.RELATIONSHIP_ATNBASELINETASK + "].from." +
                    ATNConstants.SELECT_POLICY);

            Map mTaskInfo = dmoTask.getInfo(context, typeSelects);
            String strTaskName	= (String) mTaskInfo.get(DomainConstants.SELECT_NAME);
            String strProjectId	= (String) mTaskInfo.get("to[" + ATNConstants.RELATIONSHIP_PROJECT_ACCESS_KEY + "].from.from[" +
                    ATNConstants.RELATIONSHIP_PROJECT_ACCESS_LIST + "].to.id");
            String strBaselineNum = (String) mTaskInfo.get("to[" + ATNConstants.RELATIONSHIP_ATNBASELINETASK + "].from." +
                    ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);
            String strBaselinePolicy = (String) mTaskInfo.get("to[" + ATNConstants.RELATIONSHIP_ATNBASELINETASK + "].from." +
                    ATNConstants.SELECT_POLICY);

            typeSelects = new StringList(3);
            typeSelects.addElement(ATNConstants.SELECT_ID);
            typeSelects.addElement(ATNConstants.SELECT_CURRENT);
            typeSelects.addElement(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);

            DomainObject dmoProject = new DomainObject(strProjectId);

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

            mlBaseline.sort(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM, "descending", "integer");

            if(mlBaseline.size() > 0)
            {
                Map mMaxBaseline = (Map) mlBaseline.get(0);
                String strMaxBaselineId			= (String) mMaxBaseline.get(ATNConstants.SELECT_ID);
                String strMaxBaselineCurrent	= (String) mMaxBaseline.get(ATNConstants.SELECT_CURRENT);
                String strMaxBaselineNum		= (String) mMaxBaseline.get(ATNConstants.SELECT_ATTRIBUTE_ATNBASELINENUM);

                if(!ATNConstants.STATE_ATNBASELINELOG_COMPLETE.equals(strMaxBaselineCurrent))
                {
                    blAccess = false;
                }

                String strWhere = DomainConstants.SELECT_NAME + " == \'" + strTaskName + "\'";

                DomainObject dmoBaseline = new DomainObject(strMaxBaselineId);

                MapList mlTask = dmoBaseline.getRelatedObjects(context,
                        ATNConstants.RELATIONSHIP_ATNBASELINETASK,
                        ATNConstants.TYPE_TASK,
                        null,
                        null,
                        false,
                        true,
                        (short)1,
                        strWhere,
                        null,
                        0);

                if(ATNConstants.POLICY_BASELINE_LOG.equals(strBaselinePolicy) || UIUtil.isNullOrEmpty(strBaselineNum))
                {
                    return true;
                }
                else if(Integer.parseInt(strMaxBaselineNum) == Integer.parseInt(strBaselineNum))
                {
                    if(ATNConstants.STATE_ATNBASELINELOG_COMPLETE.equals(strMaxBaselineCurrent))
                    {
                        return true;
                    }
                }

                if(mlTask.size() > 0)
                {
                    blAccess = false;
                }
            }
        }
        catch (Exception e)
        {
            e.printStackTrace();
        }

        return blAccess;
    }

    /**
     * 도면 수정 권한 체크
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public boolean checkEditCADDrawing(Context context, String[] args) throws Exception
    {
        HashMap programMap	= (HashMap) JPO.unpackArgs(args);
        String objectId		= (String) programMap.get("objectId");
        String contextUser	= context.getUser();

        boolean bCheck		= false;
        boolean bCheckRole	= false;

        StringList selects = new StringList(3);
        selects.add(DomainConstants.SELECT_OWNER);
        selects.add(DomainConstants.SELECT_POLICY);
        selects.add(DomainConstants.SELECT_CURRENT);

        DomainObject dmo = new DomainObject(objectId);

        Map mInfo = dmo.getInfo(context, selects);
        String strOwner		= (String) mInfo.get(DomainConstants.SELECT_OWNER);
        String strPolicy	= (String) mInfo.get(DomainConstants.SELECT_POLICY);
        String strCurrent	= (String) mInfo.get(DomainConstants.SELECT_CURRENT);

        bCheckRole = checkCADDrawingAdmin(context, contextUser, strPolicy);
        //CAD Drawing  수정 버튼 활성화 조건 변경
        if((DomainConstants.STATE_CADDRAWING_PRELIMINARY.equals(strCurrent) && strOwner.equals(contextUser)) || bCheckRole)
        {
            bCheck = true;
        }

        //mod 2021-12-09 modify권한 확인
        Access access = dmo.getAccessMask(context);
        if(DomainConstants.STATE_CADDRAWING_RELEASE.equals(strCurrent) && access.hasModifyAccess())
        {
            bCheck = true;
        }
        return bCheck;
    }

    public boolean checkCADDrawingAdmin(Context context, String[] args) throws Exception
    {
        return checkCADDrawingAdmin(context, args[0], args[1]);
    }

    /**
     * 도면 Policy별 Admin Role 체크
     * @param context
     * @param contextUser
     * @param policy
     * @return
     * @throws Exception
     */
    public boolean checkCADDrawingAdmin(Context context, String contextUser, String policy) throws Exception
    {
        boolean bCheckRole = false;

        if(ATNConstants.POLICY_ATNDRWDRAWING.equals(policy))
        {
            // 기구 도면
            bCheckRole = isAssigned(context, contextUser, ATNConstants.ROLE_ATNDRWDRAWINGADMIN);
        }
        else if(ATNConstants.POLICY_ATNDSNDRAWING.equals(policy))
        {
            // 디자인 도면
            bCheckRole = isAssigned(context, contextUser, ATNConstants.ROLE_ATNDSNDRAWINGADMIN);
        }
        else if(ATNConstants.POLICY_ATNHADDRAWING.equals(policy))
        {
            // 홈페이지 도면
            bCheckRole = isAssigned(context, contextUser, ATNConstants.ROLE_ATNHADDRAWINGADMIN);
        }
        else if(ATNConstants.POLICY_ATNPCBDRAWING.equals(policy))
        {
            // PCB 도면
            bCheckRole = isAssigned(context, contextUser, ATNConstants.ROLE_ATNPCBDRAWINGADMIN);
        }
        else if(ATNConstants.POLICY_ATNSCHDRAWING.equals(policy))
        {
            // 회로 도면
            bCheckRole = isAssigned(context, contextUser, ATNConstants.ROLE_ATNSCHDRAWINGADMIN);
        }

        return bCheckRole;
    }

    /**
     * 문서 생성 시 Role에 따라 보안 등급 속성 Range 리스트 보여주기
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public StringList getDocumentSecurityList(Context context, String[] args) throws Exception
    {
        String contextUser = context.getUser();

        StringList slATNSecurityLevel = FrameworkUtil.getRanges(context, ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL);

        if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNDOCUMENTSADMIN))
        {
            // 문서 관리자
        }
        else if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNSECURITYLEVEL1STCLASSCREATOR))
        {
            // 1급 생성자
        }
        else if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNSECURITYLEVEL2STCLASSCREATOR))
        {
            // 2급 생성자
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_03_1STCLASS);
        }
        else if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNSECURITYLEVEL3STCLASSCREATOR))
        {
            // 3급 생성자
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_03_1STCLASS);
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_04_2STCLASS);
        }
        else if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNSECURITYLEVELCONFIDENTIALCREATOR))
        {
            // 대외비 생성자
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_03_1STCLASS);
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_04_2STCLASS);
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_05_3STCLASS);
        }
        else if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNSECURITYLEVELGENERALCREATOR))
        {
            // 일반 생성자
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_02_CONFIDENTIAL);
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_03_1STCLASS);
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_04_2STCLASS);
            slATNSecurityLevel.remove(ATNConstants.ATTRIBUTE_ATNSECURITYLEVEL_RANGE_05_3STCLASS);
        }
        else
        {
            slATNSecurityLevel.clear();
        }

        return slATNSecurityLevel;
    }

    /**
     * 문서 수정 시 Role에 따라 보안 등급 속성 Range 리스트 보여주기
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public HashMap getDocumentSecurityRange(Context context, String[] args) throws Exception
    {
        HashMap ViewMap = new HashMap();
        StringList slActualValue		= new StringList();
        StringList slDisplayValue		= new StringList();
        StringList slATNSecurityLevel 	= getDocumentSecurityList(context, args);
        Locale strLocale = context.getLocale();

        for(int i=0; i<slATNSecurityLevel.size(); i++)
        {
            String strValue = (String) slATNSecurityLevel.get(i);
            String strDisplay = EnoviaResourceBundle.getProperty(context, "emxFrameworkStringResource", strLocale, "emxFramework.Range.ATNSecurityLevel." + strValue);

            slActualValue.add(strValue);
            slDisplayValue.add(strDisplay);
        }

        ViewMap.put("field_choices", slActualValue);
        ViewMap.put("field_display_choices", slDisplayValue);

        return ViewMap;
    }

    /**
     * 최초 리비전의 문서 수정 시 Owner, 관리자 보안 등급 수정 가능, 이후의 리비전에서는 관리자만 수정 가능
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public boolean isDocumentSecurityLevel (Context context, String[] args) throws Exception
    {
        boolean bCheck = false;
        Map programMap = (Map) JPO.unpackArgs(args);
        HashMap requestMap = (HashMap) programMap.get("requestMap");
        String objectId		= (String) requestMap.get("objectId");
        String contextUser	= context.getUser();

        DomainObject dmoDoc = new DomainObject(objectId);
        String preRevision = dmoDoc.getPreviousRevision(context).getRevision();

        if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNDOCUMENTSADMIN))
        {
            bCheck = true;
        }
        else if(UIUtil.isNullOrEmpty(preRevision))
        {
            bCheck = true;
        }

        return bCheck;
    }

    /**
     * 선행, 하위 타스크/페이즈/게이트 추가
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkTaskAdd(Context context, String objectId) throws Exception
    {
        boolean access = false ;
        boolean checkPM = checkProjectPM(context, objectId);
        boolean pmsAdmin = context.isAssigned("ATNPMSAdmin");

        DomainObject dmo = new DomainObject(objectId);
        String strType = dmo.getInfo(context, DomainConstants.SELECT_TYPE);

        if(!ProgramCentralConstants.TYPE_PROJECT_SPACE.equals(strType))
        {
            objectId = dmo.getInfo(context, "to[" + ProgramCentralConstants.RELATIONSHIP_PROJECT_ACCESS_KEY + "].from.from["
                    + ProgramCentralConstants.RELATIONSHIP_PROJECT_ACCESS_LIST + "].to.id");

            dmo.setId(objectId);
        }

        String strCurrent	= dmo.getInfo(context, DomainConstants.SELECT_CURRENT);
        String strWhere		= DomainConstants.SELECT_CURRENT + " == " + ATNConstants.STATE_ATNBASELINELOG_CREATE;

        MapList mlBaseline = dmo.getRelatedObjects(context,
                ProgramCentralConstants.RELATIONSHIP_BASELINE_LOG,
                ATNConstants.TYPE_ATNBASELINELOG,
                null,
                null,
                false,
                true,
                (short)1,
                strWhere,
                null,
                0);

        if(checkPM)
        {
            if(pmsAdmin) {
                access = true;
            }
            else if(ProgramCentralConstants.STATE_PROJECT_SPACE_CREATE.equals(strCurrent) || ProgramCentralConstants.STATE_PROJECT_SPACE_ASSIGN.equals(strCurrent))
            {
                access = true;
            }
            else if(ProgramCentralConstants.STATE_PROJECT_SPACE_ACTIVE.equals(strCurrent))
            {
                if(mlBaseline.size() > 0)
                {
                    access = true;
                }
            }
        }

        return access ;
    }

    /**
     * ATNGateMeeting Role이 있는지 체크
     * @param context
     * @param args
     * @return
     * @throws Exception
     */
    public StringList checkATNGateMeeting (Context context, String[] args) throws Exception
    {
        Map programMap =   (Map)JPO.unpackArgs(args);
        MapList objectList = (MapList)programMap.get("objectList");

        StringList returnStringList = new StringList(objectList.size());
        String contextUser	= context.getUser();

        if(isAssigned(context, contextUser, ATNConstants.ROLE_ATNGATEMEETING))
        {
            for (int i=0; i<objectList.size(); i++)
            {
                returnStringList.addElement("true");
            }
        }
        else
        {
            for (int i=0; i<objectList.size(); i++)
            {
                returnStringList.addElement("false");
            }
        }

        return returnStringList;
    }
    /**
     * 품목 복사 유무 확인
     * 전자 부품 복사 되지 않음
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean hasCloneAccess(Context context, String objectId) throws Exception
    {
        boolean access = false ;
        DomainObject dmo = new DomainObject(objectId);
        String remark = dmo.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_CLASSIFIED_ITEM+ "].from.attribute[ATNRemark]");
        String partClass = dmo.getInfo(context, "to[" + ATNConstants.RELATIONSHIP_CLASSIFIED_ITEM+ "].from.name");
//    	System.out.println(partClass+" : "+remark);
        if(ATNStringUtil.isNotEmpty(remark)&&"N".equalsIgnoreCase(remark)){
            access = false;
        }else{
            access = true;
        }
        return access ;
    }

    /**
     * Object의 결재자 인지 체크
     * @param context
     * @param objectId
     * @return
     * @throws Exception
     */
    public boolean checkApprover(Context context, String objectId) throws Exception {
        boolean access = false ;
        String sLoginUser = context.getUser();

        DomainObject dmo = new DomainObject(objectId);
        StringList routeList = dmo.getInfoList(context, "from[" + ATNConstants.RELATIONSHIP_OBJECT_ROUTE + "].to.id");

        int routeSize = routeList.size();

        if(routeSize > 0) {
            DomainObject route = new DomainObject();
            String sWhere = "owner == '" + sLoginUser + "'";

            for(int i=0; i<routeList.size(); i++) {
                route.setId((String) routeList.get(i));
                MapList inboxTaskList = route.getRelatedObjects(context, ATNConstants.RELATIONSHIP_ROUTE_TASK, ATNConstants.TYPE_INBOX_TASK, ATNConstants.EMPTY_STRINGLIST
                        ,ATNConstants.EMPTY_STRINGLIST, true, false, (short) 0, sWhere, ATNConstants.EMPTY_STRING, 0);

                if(inboxTaskList.size() > 0) {
                    access = true;
                    break;
                }
            }
        }

        return access;
    }

    /**
     * @param context
     * @param args
     * @return
     * @throws Exception
     *
     * 2020.03.25 일정변경보고서가 연결유무확인
     */
    public boolean checkAccessBaselineApproval( Context context , String[] args) throws Exception
    {
        boolean access = false ;

        try {
            ATNPMSCommon_mxJPO jpo = new ATNPMSCommon_mxJPO(context, null);

            Map paramMap = (HashMap) JPO.unpackArgs(args);
            String objectId = (String) paramMap.get("objectId");

            DomainObject baselineObj = new DomainObject(objectId);
            boolean isBaselineRelDoc = baselineObj.hasRelatedObjects(context, ATNConstants.RELATIONSHIP_ATNBASELINEDOC, true);

            // Project PM
            if(!access){
                access = checkProjectPM(context, objectId) && isBaselineRelDoc;

            }
            if(!access){
                access = checkApprover(context, objectId) && isBaselineRelDoc;
            }
            if(!access) {
                access = jpo.checkDownloadRequiredUser(context, objectId) && isBaselineRelDoc;
            }
			/*if(isBaselineRelDoc){
				access = true;
			}*/
        } catch(Exception e) {
            e.printStackTrace();
        }

        return access ;
    }
}
