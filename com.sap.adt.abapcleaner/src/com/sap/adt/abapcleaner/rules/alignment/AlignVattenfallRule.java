package com.sap.adt.abapcleaner.rules.alignment;

import java.time.LocalDate;

import com.sap.adt.abapcleaner.base.ABAP;
import com.sap.adt.abapcleaner.parser.Code;
import com.sap.adt.abapcleaner.parser.Command;
import com.sap.adt.abapcleaner.parser.Term;
import com.sap.adt.abapcleaner.parser.Token;
import com.sap.adt.abapcleaner.parser.TokenSearch;
import com.sap.adt.abapcleaner.parser.TokenType;
import com.sap.adt.abapcleaner.programbase.UnexpectedSyntaxAfterChanges;
import com.sap.adt.abapcleaner.programbase.UnexpectedSyntaxBeforeChanges;
import com.sap.adt.abapcleaner.programbase.UnexpectedSyntaxException;
import com.sap.adt.abapcleaner.rulebase.ConfigBoolValue;
import com.sap.adt.abapcleaner.rulebase.ConfigValue;
import com.sap.adt.abapcleaner.rulebase.Profile;
import com.sap.adt.abapcleaner.rulebase.RuleForCommands;
import com.sap.adt.abapcleaner.rulebase.RuleGroupID;
import com.sap.adt.abapcleaner.rulebase.RuleID;
import com.sap.adt.abapcleaner.rulebase.RuleReference;
import com.sap.adt.abapcleaner.rulebase.RuleSource;
import com.sap.adt.abapcleaner.rulehelpers.AlignCell;
import com.sap.adt.abapcleaner.rulehelpers.AlignCellTerm;
import com.sap.adt.abapcleaner.rulehelpers.AlignCellToken;
import com.sap.adt.abapcleaner.rulehelpers.AlignColumn;
import com.sap.adt.abapcleaner.rulehelpers.AlignLine;
import com.sap.adt.abapcleaner.rulehelpers.AlignTable;

public class AlignVattenfallRule extends RuleForCommands {
	public static final String DISPLAY_NAME = "Align Vattenfall specific rules";

	public enum ValueColumns {
		VARIABLE, VALUE_KEYWORD, EXPRESSION;

		public int getValue() {
			return this.ordinal();
		}
	}

	public enum MethChainColumns {
		METHOD_CALL, PARAMETER;

		public int getValue() {
			return this.ordinal();
		}
	}

	public static final int MAX_VALUE_COLUMN_COUNT = 2;

	public static final int MAX_METHOD_CHAIN_COLUMN_COUNT = 2;

	public enum ContentType {
		VALUE_STATEMENT, METHOD_CHAIN;
	}

	private final static RuleReference[] references = new RuleReference[] {
			new RuleReference(RuleSource.VATTENFALL_SPECIFIC,
					"Put the Value statement on the next line from the created variable assignment"),
			new RuleReference(RuleSource.VATTENFALL_SPECIFIC, "Each method call in a method chain gets its own line"),
			new RuleReference(RuleSource.VATTENFALL_SPECIFIC,
					"Put the Value statement on the next line from the created variable assignment") };

//	private class TableStart {
//		public final int startIndent;
//		public final boolean continueOnSameLine;
//		public final boolean forceTableToNextLine;
//		public final int earlyIndent;
//
//		private TableStart(int startIndent, boolean continueOnSameLine, boolean forceTableToNextLine, int earlyIndent) {
//			this.startIndent = startIndent;
//			this.continueOnSameLine = continueOnSameLine;
//			this.forceTableToNextLine = forceTableToNextLine;
//			this.earlyIndent = earlyIndent;
//		}
//	}

	@Override
	public RuleID getID() {
		return RuleID.ALIGN_VATTENFALL;
	}

	@Override
	public RuleGroupID getGroupID() {
		return RuleGroupID.ALIGNMENT;
	}

	@Override
	public String getDisplayName() {
		return DISPLAY_NAME;
	}

	@Override
	public String getDescription() {
		return "Aligns VALUE statements and method chains.";
	}

	@Override
	public LocalDate getDateCreated() {
		return LocalDate.of(2025, 4, 25);
	}

	@Override
	public RuleReference[] getReferences() {
		return references;
	}

	@Override
	public boolean isEssential() {
		return true;
	}

	@Override
	public String getExample() {
		return "" + LINE_SEP + "    DATA(lt_customer_ids) = VALUE #( ( 1 ) ( 2 ) ( 3 ) )." + 
					LINE_SEP + 
					LINE_SEP + "    DATA(lt_r_customer_ids) = VALUE tt_r_customer_ids( FOR <fs_id> IN lt_customer_ids SIGN = 'I' option = 'EQ' ( low = <fs_id ) )." +
					LINE_SEP + 
					LINE_SEP + "    lt_r_customer_ids = VALUE tt_r_customer_ids( FOR <fs_id> IN lt_customer_ids SIGN = 'I' option = 'EQ' ( low = <fs_id ) )." +
					LINE_SEP + 
					LINE_SEP + "    lo_object=>get_instance()->set_data( is_data = ls_data)->execute()." +
					LINE_SEP;
	}

	final ConfigBoolValue configValueStatementOnNewLine = new ConfigBoolValue(this, "ValueStatementOnNewLine",
			"Put VALUE statement on its own line", true);
	final ConfigBoolValue configMethodChainingOnNewLine = new ConfigBoolValue(this, "MethodChainingOnNewLine",
			"Put method calls each on a new line", true);
	final ConfigBoolValue configForStatementsOnNewLine = new ConfigBoolValue(this, "ForStatementsOnNewLine",
			"Put FOR statements on its own line", true);
	final ConfigBoolValue configIndentParamOnlyOnce = new ConfigBoolValue(this, "IndentParamOnlyOnce",
			"Indent parameters always once( this is applied in Align parameters and components)", true);

	private final ConfigValue[] configValues = new ConfigValue[] { configValueStatementOnNewLine,
			configMethodChainingOnNewLine, configForStatementsOnNewLine, configIndentParamOnlyOnce };

	@Override
	public ConfigValue[] getConfigValues() {
		return configValues;
	}

	public AlignVattenfallRule(Profile profile) {
		super(profile);
		initializeConfiguration();
	}

	public boolean executeOn(Code code, Command command) throws UnexpectedSyntaxAfterChanges {
		return executeOn(code, command, ABAP.NO_RELEASE_RESTRICTION);
	}

	@Override
	protected boolean executeOn(Code code, Command command, int releaseRestriction)
			throws UnexpectedSyntaxAfterChanges {
		if (command.isCommentLine())
			return false;

		Token firstCode = command.getFirstCodeToken();
		if (firstCode == null)
			return false;

		boolean changed = false;

		Token period = command.getLastNonCommentToken();
		// align VALUE #( )| type( )
		if (firstCode.matchesOnSiblings(true, TokenSearch.ASTERISK, "VALUE")
				&& configValueStatementOnNewLine.getValue()) {
			Token parentToken = firstCode;
			int baseIndent = command.getFirstToken().getStartIndexInLine();
			if (alignValueAndFor(code, command, parentToken, period, baseIndent, baseIndent)) {
				changed = true;
			}
		}

		// align method chains like
		// lo_object=>get_instance()->set_data( is_data = ls_data)->execute().
		if (firstCode.isIdentifier() && firstCode.getText().endsWith("(")
				&& firstCode.getNextCodeSibling().isIdentifier()
				&& firstCode.getNextCodeSibling().getText().startsWith(")->")
				&& configMethodChainingOnNewLine.getValue()) {
			Token parentToken = firstCode;
			int baseIndent = command.getFirstToken().getStartIndexInLine();
			if (alignMethodChain(code, command, parentToken, period, baseIndent, baseIndent)) {
				changed = true;
			}
		}

		// align FOR statements on a new line
		if (firstCode.matchesDeep(true, TokenSearch.ASTERISK, "FOR") && configForStatementsOnNewLine.getValue()) {
			Token parentToken = firstCode;
			int baseIndent = command.getFirstToken().getStartIndexInLine();
			if (alignFor(code, command, parentToken, period, baseIndent, baseIndent)) {
				changed = true;
			}

		}

		return changed;
	}

	private Token addDataKeyword(Token dataToken, AlignTable table) throws UnexpectedSyntaxException {

		Token assignmentToken = dataToken.getNextCodeSibling();
		AlignCell keywordCell;

		while (!assignmentToken.isAssignmentOperator()) {
			assignmentToken = assignmentToken.getNextCodeSibling();
		}

		keywordCell = new AlignCellTerm(Term.createForTokenRange(dataToken, assignmentToken));
		AlignLine line = table.addLine();
		line.setCell(ValueColumns.VARIABLE.getValue(), keywordCell);
		return assignmentToken;
	}

	private Token addExpression(Token expressionToken, Token endToken, AlignTable table)
			throws UnexpectedSyntaxException {
		AlignLine line = table.addLine();

		AlignCell expressionCell = new AlignCellTerm(Term.createForTokenRange(expressionToken, endToken));

		line.setCell(ValueColumns.VALUE_KEYWORD.getValue(), expressionCell);

		return endToken;
	}

	private Token indentForStatement(Token startToken, Token endToken, AlignTable table)
			throws UnexpectedSyntaxException {
		AlignLine line = table.addLine();

		if (startToken.matchesDeep(true, TokenSearch.ASTERISK, "FOR") && configForStatementsOnNewLine.getValue()) {
			Token forToken = startToken.getNextTokenOfTypeAndText(TokenType.KEYWORD, "FOR");
			forToken.setWhitespace(1, forToken.getPrevCodeToken().getStartIndexInLine() + ABAP.INDENT_STEP);
		}

		AlignCell newCell = new AlignCellTerm(Term.createForTokenRange(startToken, endToken));
		line.setCell(ValueColumns.VALUE_KEYWORD.getValue(), newCell);

		return endToken;
	}

	private final Boolean alignValueAndFor(Code code, Command command, Token parentToken, Token endToken,
			int baseIndent, int minimumIndent) throws UnexpectedSyntaxAfterChanges {
		Command[] changedCommands = null;
		boolean changed = false;
		AlignTable table = new AlignTable(MAX_VALUE_COLUMN_COUNT);
		table.addLine();

		try {
			Token token = command.getFirstCodeToken();
//			if (configValueStatementOnNewLine.getValue()) {
			token = addDataKeyword(token, table);
//			}

			token = token.getNextCodeToken();
			if (!token.isKeyword("VALUE")) {
				return false;
			}

			token = addExpression(token, command.getLastNonCommentToken(), table);

			AlignColumn column = table.getColumn(0);

			column.setForceLineBreakAfter(true);

			changedCommands = table.align(baseIndent, minimumIndent, true);

			if (changedCommands != null && changedCommands.length > 0) { // changedCommands can only contain this
																			// current command
				changed = true;
			}

			return changed;

		} catch (UnexpectedSyntaxException ex) {
			(new UnexpectedSyntaxBeforeChanges(this, ex)).addToLog();
			return false;
		}
	}

	private final Boolean alignMethodChain(Code code, Command command, Token parentToken, Token endToken,
			int baseIndent, int minimumIndent) throws UnexpectedSyntaxAfterChanges {
		boolean changed = false;
		Command[] changedCommands;
		AlignTable table = new AlignTable(MAX_METHOD_CHAIN_COLUMN_COUNT);
		table.addLine();
		try {
			Token token = command.getFirstCodeToken();

			while (!token.isPeriod() && token != null) {

				AlignCell chainCell = new AlignCellToken(token);
				AlignCell variablesCell;
				AlignLine line;
				if (token.getText().equals(")") && token.getNextSibling().isPeriod())
					break;

				line = table.addLine();

				line.setCell(MethChainColumns.METHOD_CALL.getValue(), chainCell);
				if (!token.equals(command.getFirstToken())) {
					chainCell.setAdditionalIndent(baseIndent + 2);
				}

				if (token.getFirstChild() != null) {
					variablesCell = new AlignCellTerm(
							Term.createForTokenRange(token.getFirstChild(), token.getLastChild()));
					line = table.addLine();
					line.setCell(MethChainColumns.PARAMETER.getValue(), variablesCell);
				}

				token = token.getNextSibling();
			}

			AlignColumn column = table.getColumn(0);

			column.setForceLineBreakAfter(true);

			changedCommands = table.align(baseIndent, minimumIndent, true);

			if (changedCommands != null && changedCommands.length > 0) { // changedCommands can only contain this
																			// current command
				changed = true;
			}

		} catch (UnexpectedSyntaxException ex) {
			(new UnexpectedSyntaxBeforeChanges(this, ex)).addToLog();
			return false;
		}
		return changed;

	}

	private final Boolean alignFor(Code code, Command command, Token parentToken, Token endToken, int baseIndent,
			int minimumIndent) throws UnexpectedSyntaxAfterChanges {
		boolean changed = false;
		Command[] changedCommands = null;
		AlignTable table = new AlignTable(MAX_VALUE_COLUMN_COUNT);
		table.addLine();
		try {
			Token token = command.getFirstCodeToken();

			token = indentForStatement(token, endToken, table);

			changedCommands = table.align(baseIndent, minimumIndent, true);

			if (changedCommands != null && changedCommands.length > 0) { // changedCommands can only contain this
																			// current command
				changed = true;
			}

			return changed;

		} catch (UnexpectedSyntaxException ex) {
			(new UnexpectedSyntaxBeforeChanges(this, ex)).addToLog();
			return false;
		}
	}
}
