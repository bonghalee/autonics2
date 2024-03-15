
import matrix.db.Context;
import com.dassault_systemes.EKLEngine.completion.CompletionJPOEvaluator;


/**
 * ${CLASSNAME}
 */
public final class AlternateCompletion_mxJPO extends CompletionJPOEvaluator {

	/**
	 * Attributes
	 */
	private final static com.dassault_systemes.EKLEngine.common.lib.implementation.StringType _STRING_0__com_dot_dassault_syst = new com.dassault_systemes.EKLEngine.common.lib.implementation.StringType("com.dassault_systemes.alternatemanagement.dataexchange.impl.AlternateCompletionForEKL");

	/**
	 * evaluate
	 * @param iContext
	 * @param iPLMIDSet
	 * @param oPLMIDSet
	 */
	public final void evaluate(matrix.db.Context iContext, com.dassault_systemes.EKLEngine.common.lib.PLMIDSet iPLMIDSet, com.dassault_systemes.EKLEngine.common.lib.PLMIDSet oPLMIDSet)	
			throws Exception {
		oPLMIDSet.setValue( com.dassault_systemes.EKLEngine.completion.lib.Completion.ExecuteJavaProcedure( iContext , _STRING_0__com_dot_dassault_syst, new com.dassault_systemes.EKLEngine.common.lib.implementation.ObjectType[] { iPLMIDSet } ) );
	}
}
