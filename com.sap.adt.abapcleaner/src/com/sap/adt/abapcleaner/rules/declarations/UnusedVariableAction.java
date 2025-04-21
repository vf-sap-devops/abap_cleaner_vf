package com.sap.adt.abapcleaner.rules.declarations;

import com.sap.adt.abapcleaner.parser.ChainElementAction;

public enum UnusedVariableAction  {
   DELETE(ChainElementAction.DELETE),
   COMMENT_OUT_WITH_ASTERISK(ChainElementAction.COMMENT_OUT_WITH_ASTERISK),
   COMMENT_OUT_WITH_QUOT(ChainElementAction.COMMENT_OUT_WITH_QUOT),
   ADD_TODO_COMMENT(ChainElementAction.ADD_TODO_COMMENT),
   IGNORE(ChainElementAction.IGNORE);

	private ChainElementAction chainElementAction;
	
	private UnusedVariableAction(ChainElementAction chainElementAction) {
		this.chainElementAction = chainElementAction;
	}

	public int getValue() { return this.ordinal(); }
	public ChainElementAction getCorrespondingChainElementAction() { return chainElementAction; }
	
	public static UnusedVariableAction forValue(int value) {
		return values()[value];
	}
}