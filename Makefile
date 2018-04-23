BASEDIR=/home/luke/workspace/jlox
CLASSPATH=${BASEDIR}
LOXSOURCEPATH=${BASEDIR}/com/craftinginterpreters/lox
TOOLSOURCEPATH=${BASEDIR}/com/craftinginterpreters/tool

.PHONY: lox
lox: gen_ast
	cd ${LOXSOURCEPATH} && javac -sourcepath ${LOXSOURCEPATH} Lox.java Scanner.java TokenType.java Token.java \
		Parser.java AstPrinter.java Expr.java Stmt.java Interpreter.java RuntimeError.java Environment.java \
		LoxCallable.java LoxFunction.java Resolver.java LoxClass.java LoxInstance.java StackFrame.java LoxArray.java \
		Runtime.java LoxNativeClass.java LoxNativeCallable.java Param.java

.PHONY: clean
clean:
	rm -f ${LOXSOURCEPATH}/*.class
	rm -f ${BASEDIR}/*.class
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
