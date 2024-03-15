import matrix.db.Context;
import matrix.db.MatrixWriter;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;

import com.matrixone.apps.domain.util.ContextUtil;
import com.matrixone.apps.domain.util.MqlUtil;
import com.matrixone.jsystem.util.ExceptionUtils;

public class aclRolesMigration_mxJPO {

    BufferedWriter writer = null;
    FileWriter warningLog = null;
    String documentDirectory = "";
    
	public aclRolesMigration_mxJPO(Context context, String[] args) throws Exception {		
		// TODO Auto-generated constructor stub
		writer = new BufferedWriter(new MatrixWriter(context));
	}
	
	public int mxMain(Context context, String[] args) throws Exception {
			
		try {			
			if(args.length < 1) 
				throw new IllegalArgumentException();
		}
	    catch (IllegalArgumentException iExp) {
	        writer.write("=================================================================\n");
	        writer.write("Wrong number of arguments.\nPlease provide directory path for creating log file as 1st argument\n");
	        writer.write(iExp.toString() + " : FAILED \n");
	        writer.write("=================================================================\n");
	        writer.close();
	        return 0;
	    }
		
		try {
			documentDirectory = args[0];

	        // documentDirectory does not ends with "/" add it
	        String fileSeparator = java.io.File.separator;
	        if(documentDirectory != null && !documentDirectory.endsWith(fileSeparator)) {
	            documentDirectory = documentDirectory + fileSeparator;
	        }

			warningLog = new FileWriter(documentDirectory + "ACL_Migration.log", false);
		}
	    catch (FileNotFoundException fEx)
	    {
	        // check if user has access to the directory
	        // check if directory exists
	        writer.write("=================================================================\n");
	        writer.write("Directory does not exist or does not have access to the directory\n");
	        writer.write("=================================================================\n");
	        writer.close();
	        return 0;
	    }
		
		try {
			mqlLogRequiredInformationWriter("=================================================================");
	        mqlLogRequiredInformationWriter("Stamp the onlyactiveacl flag on Roles");
	        mqlLogRequiredInformationWriter("=================================================================");
	        	        
	        long start = System.currentTimeMillis();
			mqlLogRequiredInformationWriter("Start Time: " + start);
			     		
	        ContextUtil.pushContext(context, null, null, null);
	        ContextUtil.startTransaction(context,true);
	        
	        migrateRole(context);
	        
	        long finish = System.currentTimeMillis();
			mqlLogRequiredInformationWriter("Finish Time: " + finish);
			
			mqlLogRequiredInformationWriter("=================================================================");
	    	mqlLogRequiredInformationWriter("Process completed in " + (finish-start) + "ms.");
	    	mqlLogRequiredInformationWriter("=================================================================");
	        
	        ContextUtil.commitTransaction(context);
	    }
	    catch (Exception ex) {
	        // abort if transition fails
	    	mqlLogRequiredInformationWriter("=================================================================");
	    	mqlLogRequiredInformationWriter("The stamping of onlyactiveacl flag on the role failed.");
	    	mqlLogRequiredInformationWriter("=================================================================");
	        writer.flush();

	        ContextUtil.abortTransaction(context);
	    }
	    finally {
	        writer.close();
	        warningLog.close();
	        ContextUtil.popContext(context);
	    }

        return 0;
	}
	
	/*
	 * This method adds onlyactiveacl stamp to the
	 * User Projects role along with its child roles
	 */
	public void migrateRole(Context context) throws Exception {
		
		String usrPrjCmd = "transition onlyactiveacl role $1 $2";
		
		try {
			mqlLogRequiredInformationWriter("Starting the transition of onlyactiveacl stamp on 'User Projects' and its child roles.");			
			MqlUtil.mqlCommand(context, usrPrjCmd, "User Projects", "propagate");
			mqlLogRequiredInformationWriter("Successfully completed the transition of onlyactiveacl stamp on 'User Projects' and its child roles.");
		}
		catch (Exception ex) {
			mqlLogRequiredInformationWriter(ex.getMessage());
    		mqlLogRequiredInformationWriter(ExceptionUtils.getStackTrace(ex));
    		throw ex;
		}		
	}

	public void mqlLogRequiredInformationWriter(String command) throws Exception {
        writer.write(command + "\n");
        writer.flush();
      
        warningLog.write(command + "\n");
        warningLog.flush();
    }
}
