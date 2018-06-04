BASEDIR=/home/luke/workspace/jlox
VENDORPATH=${BASEDIR}/vendor
VENDOR_JARPATHS=${VENDORPATH}/hamcrest-core-1.3.jar:${VENDORPATH}/junit-4.12.jar:${VENDORPATH}/jline-2.10.jar
CLASSPATH=${BASEDIR}
LOXSOURCEPATH=${BASEDIR}/com/craftinginterpreters/lox
TOOLSOURCEPATH=${BASEDIR}/com/craftinginterpreters/tool
TESTSOURCEPATH=${BASEDIR}/com/craftinginterpreters/test
JAVAC_OPTS=

TEST_FILES = AstPrinterTest.java InterpreterTest.java

.PHONY: lox
lox: gen_ast
	cd ${LOXSOURCEPATH} && javac ${JAVAC_OPTS} -sourcepath ${LOXSOURCEPATH} -cp ${VENDOR_JARPATHS} Lox.java Scanner.java TokenType.java Token.java \
		Parser.java AstPrinter.java Expr.java Stmt.java Interpreter.java RuntimeError.java Environment.java \
		LoxCallable.java LoxFunction.java Resolver.java LoxClass.java LoxModule.java LoxInstance.java StackFrame.java \
		Runtime.java LoxNativeClass.java LoxNativeCallable.java Param.java LoxUtil.java

.PHONY: clean
clean:
	rm -f ${LOXSOURCEPATH}/*.class
	rm -f ${TESTSOURCEPATH}/*.class
	rm -f ${BASEDIR}/*.class
	rm -f ${TESTSOURCEPATH}/*.class
	rm -f ${LOXSOURCEPATH}/{Expr,Stmt}.java

.PHONY: repl
repl: lox
	java -cp ${CLASSPATH}:${VENDOR_JARPATHS} com.craftinginterpreters.lox.Lox

ast_printer: lox
	java -cp ${CLASSPATH}:${VENDOR_JARPATHS} com.craftinginterpreters.lox.AstPrinter "1+(2+3)==5;"

.PHONY: tool
tool:
	javac ${JAVAC_OPTS} -sourcepath ${TOOLSOURCEPATH} -cp ${VENDOR_JARPATHS} ${TOOLSOURCEPATH}/GenerateAst.java

.PHONY: gen_ast
gen_ast: tool
	java -cp ${CLASSPATH}:${VENDOR_JARPATHS} com.craftinginterpreters.tool.GenerateAst ${LOXSOURCEPATH}

.PHONY: test
test: lox
	cd ${TESTSOURCEPATH} && javac ${JAVAC_OPTS} -sourcepath ${TESTSOURCEPATH}:${BASEDIR} -cp ${VENDOR_JARPATHS} MyRunner.java $(TEST_FILES) && \
	java -cp ${BASEDIR}:${VENDOR_JARPATHS} com.craftinginterpreters.test.MyRunner

