BASEDIR=/home/luke/workspace/jlox
VENDORPATH=${BASEDIR}/vendor
VENDOR_JARPATHS=${VENDORPATH}/hamcrest-core-1.3.jar:${VENDORPATH}/junit-4.12.jar
CLASSPATH=${BASEDIR}
LOXSOURCEPATH=${BASEDIR}/com/craftinginterpreters/lox
TOOLSOURCEPATH=${BASEDIR}/com/craftinginterpreters/tool
TESTSOURCEPATH=${BASEDIR}/com/craftinginterpreters/test

TEST_FILES = AstPrinterTest.java InterpreterTest.java

.PHONY: lox
lox: gen_ast
	cd ${LOXSOURCEPATH} && javac -sourcepath ${LOXSOURCEPATH} Lox.java Scanner.java TokenType.java Token.java \
		Parser.java AstPrinter.java Expr.java Stmt.java Interpreter.java RuntimeError.java Environment.java \
		LoxCallable.java LoxFunction.java Resolver.java LoxClass.java LoxInstance.java StackFrame.java LoxArray.java \
		Runtime.java LoxNativeClass.java LoxNativeCallable.java Param.java

.PHONY: clean
clean:
	rm -f ${LOXSOURCEPATH}/*.class
	rm -f ${TESTSOURCEPATH}/*.class
	rm -f ${BASEDIR}/*.class
	rm -f ${TESTSOURCEPATH}/*.class
	rm -f ${LOXSOURCEPATH}/{Expr,Stmt}.java

.PHONY: repl
repl: lox
	java -cp ${CLASSPATH} com.craftinginterpreters.lox.Lox

ast_printer: lox
	java -cp ${CLASSPATH} com.craftinginterpreters.lox.AstPrinter "1+(2+3)==5;"

.PHONY: tool
tool:
	javac -sourcepath ${TOOLSOURCEPATH} ${TOOLSOURCEPATH}/GenerateAst.java

.PHONY: gen_ast
gen_ast: tool
	java -cp ${CLASSPATH} com.craftinginterpreters.tool.GenerateAst ${LOXSOURCEPATH}

.PHONY: test
test: lox
	cd ${TESTSOURCEPATH} && javac -sourcepath ${TESTSOURCEPATH}:${BASEDIR} -cp ${VENDOR_JARPATHS} MyRunner.java $(TEST_FILES) && \
	java -cp ${BASEDIR}:${VENDOR_JARPATHS} com.craftinginterpreters.test.MyRunner

