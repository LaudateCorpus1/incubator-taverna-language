package uk.org.taverna.scufl2.api.iterationstrategy;

import uk.org.taverna.scufl2.api.common.Child;
import uk.org.taverna.scufl2.api.common.Visitor;

/**
 * @author Alan R Williams
 *
 */
public class IterationStrategy implements Child<IterationStrategyStack>,
		IterationStrategyParent {

	private IterationStrategyStack parent;
	private IterationStrategyNode rootStrategyNode;

	public IterationStrategy() {
		super();
	}

	@Override
	public boolean accept(Visitor visitor) {
		if (visitor.visitEnter(this) && getRootStrategyNode() != null) {
			getRootStrategyNode().accept(visitor);
		}
		return visitor.visitLeave(this);
	}

	public IterationStrategyStack getParent() {
		return parent;
	}

	public IterationStrategyNode getRootStrategyNode() {
		return rootStrategyNode;
	}

	public void setParent(IterationStrategyStack parent) {
		if (this.parent != null && this.parent != parent) {
			this.parent.remove(this);
		}
		this.parent = parent;
		if (parent != null && !parent.contains(this)) {
			parent.add(this);
		}
	}

	public void setRootStrategyNode(IterationStrategyNode rootStrategyNode) {
		this.rootStrategyNode = rootStrategyNode;
	}
}