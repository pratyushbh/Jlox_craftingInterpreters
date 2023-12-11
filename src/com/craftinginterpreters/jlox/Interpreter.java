package com.craftinginterpreters.jlox;

import com.craftinginterpreters.jlox.Expr.Assign;
import com.craftinginterpreters.jlox.Expr.Binary;
import com.craftinginterpreters.jlox.Expr.Call;
import com.craftinginterpreters.jlox.Expr.Grouping;
import com.craftinginterpreters.jlox.Expr.Logical;
import com.craftinginterpreters.jlox.Expr.Unary;
import com.craftinginterpreters.jlox.Expr.Variable;
import com.craftinginterpreters.jlox.Stmt.Block;
import com.craftinginterpreters.jlox.Stmt.Function;
import com.craftinginterpreters.jlox.Stmt.If;
import com.craftinginterpreters.jlox.Stmt.Print;
import com.craftinginterpreters.jlox.Stmt.Return;
import com.craftinginterpreters.jlox.Stmt.Var;
import com.craftinginterpreters.jlox.Stmt.While;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;

//We could've used the interpreter design pattern but it would've made stuff messy.
//So we use the Visitor Patter to implement the Interpreter.
//Unlike expressions, statements produce no value so their return type is Void not object.

class Interpreter implements Expr.Visitor<Object>,Stmt.Visitor<Void>{
	//Visit methods are the guts of interpreter class we need to wrap up a skin around them to interface with rest of the program.
	//this interpret method is going to take the list of statements and going to execute them as per their requirement;

	//private Environment environment = new Environment();
	//Running environment as a field directly so variables stay in memory as long as the interpreter is running.
	//Implementing the clock() native function by implementing it in global environment 
	final Environment globals = new Environment();
	private final Map<Expr, Integer> locals=new HashMap<>();
	private Environment environment=globals;
	//Implementing clock() fn in global environment
	Interpreter(){
		globals.define("clock", new LoxCallable() {
			@Override 
			public int arity() {return 0;}
			
			@Override 
			public Object call(Interpreter interpreter,List<Object> arguments) {
				return (double)System.currentTimeMillis()/1000.0;
			}
			@Override
			public String toString() {return "<native fn>";}
		});
	}
	void interpret(List<Stmt> statements) {
		try {
			for(Stmt statement: statements) {
				execute(statement);
			}
		}catch(RuntimeError error) {
			Lox.runtimeError(error);
		}
	}
	private Void execute(Stmt stmt) {
		return stmt.accept(this);
	}
	void resolve(Expr expr,int depth) {
		locals.put(expr,depth);
	}
	private String stringify(Object object) {
		if(object==null) return "nil";
		if(object instanceof Double) {
			String text=object.toString();
			if(text.endsWith(".0")) {
				text=text.substring(0,text.length()-2);
			}
			return text;
		}
		return object.toString();
	}
	@Override
	//unlike expressions statement doesn't produce any value.
	//So we are going to visit both the type of statements and their return type will be 'Void'
	//Evaluate the expression of statement and discard it.
	public Void visitExpressionStmt(Stmt.Expression stmt) {
		evaluate(stmt.expression);
		return null;
	}
	@Override
	//Before discarding the value in print statement we Stringify it and then print it in console.
	public Void visitPrintStmt(Stmt.Print stmt) {
		Object value=evaluate(stmt.expression);
		System.out.println(stringify(value));
		return null;
	}
	@Override
	//These are atomic bits of our language so we just return the value of the expression.
	//literal is a `bit of syntax` that produce a value.
	//A literal comes from parser domain, Value is a interpreter concept, part of runtime's world as a lot of values are generated
	//during computation and don't exist anywhere in code.
	//In our case we have already scanned value during scanner phase and then the val)ue is reflected in literal syntax tree from which 
	//interpreter takes the value and return it.
	public Object visitLiteralExpr(Expr.Literal expr) {
		return expr.value;
	}

	@Override
	public Object visitBinaryExpr(Binary expr) {
		Object left=evaluate(expr.left);
		Object right=evaluate(expr.right);
		
		switch(expr.operator.type) {
		//Comparison operators are same as Arithmetic operator but the only difference is that 
		//they produce the value of the different(i.e Boolean) from the operands used to evaluate the value.
		case GREATER:
			checkNumberOperands(expr.operator,left,right);
			return (double)left>(double)right;
		case GREATER_EQUAL:
			checkNumberOperands(expr.operator,left,right);
			return (double)left>=(double)right;
		case LESS:
			checkNumberOperands(expr.operator,left,right);
			return (double)left<(double)right;
		case LESS_EQUAL:
			checkNumberOperands(expr.operator,left,right);
			return (double)left<=(double)right;
		//Arithmetic operators
		case MINUS:
			checkNumberOperands(expr.operator,left,right);
			return (double)left-(double)right;
		//In case of Plus,it is overloaded to support both Numbers and Strings
		//we applied the arithmetic Plus operation and the String concatenation operation.
		//We dynamically check the type of operand and choose the appropriate operation.
		//this is why we need our object type to support `instanceof`.
		//The thing with Plus is that it already checks the type of operand so it will directly throw the RuntimeError if operands
		//are not matched.
		case PLUS:
			if(left instanceof Double && right instanceof Double) {
				return (double)left +(double)right;
			}
			if(left instanceof String && right instanceof String) {
				return (String)left+ (String)right;
			}
			throw new RuntimeError(expr.operator,"Operands must be two numbers or two strings");
		case SLASH:
			checkNumberOperands(expr.operator,left,right);
			return (double)left/(double)right;
		case STAR:
			checkNumberOperands(expr.operator,left,right);
			return (double)left*(double)right;
		//Unlike the comparison operator that requires numbers, the equality operators
		//support operands of any types even mixed ones.
		case BANG_EQUAL: return !isEqual(left,right);
		case EQUAL_EQUAL: return isEqual(left,right);
		}
		
		//Unreachable
		return null;
	}
	//We have to deal nil/null specially so that we don't throw a NullPointerException if we try to call equals() on null.
	//otherwise the Java's equals() method on Boolean,Double and String have the behavior we want from lox.
	private boolean isEqual(Object a,Object b) {
		if(a==null&&b==null) return true;
		if(a==null) return false;
		return a.equals(b);
	}
	@Override
	//Grouping expression return the evaluated expression within the parentheses.
	public Object visitGroupingExpr(Grouping expr) {
		return evaluate(expr.expression);
	}
	//this function simply evaluate the Expression.
	private Object evaluate(Expr expr) {
		return expr.accept(this);
	}

	@Override
	//Unary operators perform there own operations on the evaluated expression
	//Our interpreter is doing post order traversal,each node evaluate its children before operating on it.
	public Object visitUnaryExpr(Unary expr) {
		Object right = evaluate(expr.right);
		
		switch(expr.operator.type) {
		case BANG:
			return !isTruthy(right);
		case MINUS:
			//the negate operation must negate from a number whether that is integer or float
			//Since statically in Java we don't know what object will be so we cast it before operation.
			//this type case happens during runtime.
			checkNumberOperand(expr.operator,right);
			return -(double) right;
		}
		
		return null;
	}
	private void checkNumberOperand(Token operator,Object operand) {
		if(operand instanceof Double) return;
		throw new RuntimeError(operator,"Operand must be a number");
	}
	private void checkNumberOperands(Token operator,Object left,Object right) {
		if(left instanceof Double && right instanceof Double) return;
		
		throw new RuntimeError(operator,"Operands must be numbers.");
	}
	//Most dynamically typed language takes the universe of value and partition them into two sets
	//one of which is truthy and other is falsy.
	private boolean isTruthy(Object object) {
		if(object==null) return false;
		if(object instanceof Boolean) return (boolean)object;
		return true;
	}
	@Override
	//If there's no initializer then the language gives a syntax error.
	//But most of the dynamically typed languages don't do that.Instead they keep it simple
	//and return the 'nil' or 'null' if they aren't explicitly initialized.
	public Void visitVarStmt(Stmt.Var stmt) {
		Object value=null;
		if(stmt.initializer!=null) {
			value=evaluate(stmt.initializer);
		}
		environment.define(stmt.name.lexeme, value);
		return null;
	}
	@Override
	public Object visitVariableExpr(Variable expr) {
		return lookUpVariable(expr.name,expr);
	}
	private Object lookUpVariable(Token name, Expr expr) {
		Integer distance=locals.get(expr);
		if(distance!=null) {
			return environment.getAt(distance,name.lexeme);
		}else {
			return globals.get(name);
		}
	}
	//Implementation of assignment is very similar to that of variable declarations, instead of creating a key value pair we just
	//have to change the value of already existing key.
	@Override
	public Object visitAssignExpr(Assign expr) {
		Object value=evaluate(expr.value);
		Integer distance=locals.get(expr);
		if(distance!=null) {
			environment.assignAt(distance,expr.name,value);
		}else {
			globals.assign(expr.name,value);
		}
		return value;
	}
	@Override
	public Void visitBlockStmt(Block stmt) {
		executeBlock(stmt.statments,new Environment(environment));
		return null;
	}
	void executeBlock(List<Stmt> statements,Environment environment) {
		Environment previous=this.environment;
		try {
			this.environment=environment;
			
			for(Stmt statement:statements) {
				execute(statement);
			}
		}finally {
			this.environment=previous;
		}
	}
	@Override 
	public Void visitClassStmt(Stmt.Class stmt) {
		Object superclass=null;
		if(stmt.superclass!=null) {
			superclass=evaluate(stmt.superclass);
			if(!(superclass instanceof LoxClass)) {
				throw new RuntimeError(stmt.superclass.name,"Superclass must be a class.");
			}
		}
		environment.define(stmt.name.lexeme, null);
		if(stmt.superclass!=null) {
			environment=new Environment(environment);
			environment.define("super", superclass);
		}
		Map<String, LoxFunction> methods=new HashMap<>();
		for(Stmt.Function method: stmt.methods) {
			LoxFunction function=new LoxFunction(method,method.name.lexeme.equals("init"),environment);
			methods.put(method.name.lexeme, function);
		}
		LoxClass klass= new LoxClass(stmt.name.lexeme,(LoxClass)superclass,methods);
		if(superclass!=null) {
			environment=environment.enclosing;
		}
		environment.assign(stmt.name, klass);
		
		return null;
	}
	@Override
	public Object visitSuperExpr(Expr.Super expr) {
		int distance=locals.get(expr);
		LoxClass superclass=(LoxClass)environment.getAt(distance,"super");
		LoxInstance object=(LoxInstance)environment.getAt(distance-1, "this");
		LoxFunction method=superclass.findMethod(expr.method.lexeme);
		if(method==null) {
			throw new RuntimeError(expr.method,"Undefined property '"+expr.method.lexeme+"'.");
		}
		return method.bind(object);
	}
	@Override
	public Void visitIfStmt(If stmt) {
		if(isTruthy(evaluate(stmt.condition))) {
			execute(stmt.thenBranch);
		} else if(stmt.elseBranch!=null) {
			execute(stmt.elseBranch);
		}
		return null;
	}
	@Override
	public Object visitLogicalExpr(Logical expr) {
		Object left=evaluate(expr.left);
		
		if(expr.operator.type==TokenType.OR) {
			if(isTruthy(left)) return left;
		}else {
			if(!isTruthy(left)) return left;
		}
		
		return evaluate(expr.right);
	}
	@Override
	public Void visitWhileStmt(While stmt) {
		while(isTruthy(evaluate(stmt.condition))) {
			execute(stmt.body);
		}
		return null;
	}
	@Override
	public Object visitCallExpr(Call expr) {
		Object callee=evaluate(expr.calle);
		
		List<Object> arguments=new ArrayList<>();
		for(Expr argument:expr.arguments) {
			arguments.add(evaluate(argument));
		}
		if(!(callee instanceof LoxCallable)) {
			throw new RuntimeError(expr.paren,"Can only call functions and classes.");
		}
		
		LoxCallable function=(LoxCallable)callee;
		if(arguments.size()!=function.arity()) {
			throw new RuntimeError(expr.paren, "Expected "+ function.arity()+" arguements but go " + arguments.size() + ".");
		}
		return function.call(this,arguments);
	}
	@Override
	public Object visitGetExpr(Expr.Get expr) {
		Object object=evaluate(expr.object);
		if(object instanceof LoxInstance) {
			return ((LoxInstance) object).get(expr.name);
		}
		
		throw new RuntimeError(expr.name,"Only instances have properties.");
	}
	@Override
	public Object visitSetExpr(Expr.Set expr) {
		Object object=evaluate(expr.object);
		
		if(!(object instanceof LoxInstance)) {
			throw new RuntimeError(expr.name,"Only instances have fields.");
		}
		
		Object value=evaluate(expr.value);
		((LoxInstance)object).set(expr.name,value);
		return value;
	}
	@Override
	public Object visitThisExpr(Expr.This expr) {
		return lookUpVariable(expr.keyword,expr);
	}
	@Override
	public Void visitFunctionStmt(Function stmt) {
		LoxFunction function = new LoxFunction(stmt,false,environment);
		environment.define(stmt.name.lexeme, function);
		return null;
	}
	@Override
	public Void visitReturnStmt(Return stmt) {
		Object value=null;
		if(stmt.value!=null) value=evaluate(stmt.value);
		
		throw new Returnval(value);
	}
	
	
	
}