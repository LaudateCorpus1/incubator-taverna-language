/**
 * 
 */
package uk.org.taverna.scufl2.validation.correctness;

import uk.org.taverna.scufl2.api.common.WorkflowBean;
import uk.org.taverna.scufl2.api.container.WorkflowBundle;
import uk.org.taverna.scufl2.validation.Validator;

/**
 * @author alanrw
 */
public class CorrectnessValidator implements Validator<CorrectnessValidationListener> {
	public void checkCorrectness(WorkflowBean bean, boolean checkComplete,
			CorrectnessValidationListener listener) {
		CorrectnessVisitor visitor = new CorrectnessVisitor(checkComplete,
				listener);
		bean.accept(visitor);
	}

	@Override
	public CorrectnessValidationListener validate(WorkflowBundle workflowBundle) {
		CorrectnessValidationListener l = new ReportCorrectnessValidationListener();
		checkCorrectness(workflowBundle, false, l);
		return l;
	}
}
