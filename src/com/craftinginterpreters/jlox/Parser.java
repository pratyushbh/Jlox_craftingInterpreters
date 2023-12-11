package com.craftinginterpreters.jlox;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.craftinginterpreters.jlox.TokenType.*;
import static com.craftinginterpreters.jlox.Stmt.*;

class Parser{
	private static class ParserError extends RuntimeException{}
	private final List<Token> tokens;
	private int current=0;
	Parser(List<Token> tokens){
		this.tokens=tokens;
	}
// temporary hack to run parser for expressions
//	Expr parse() {
//		try {
//			return expression();
//		} catch(ParserError error) {
//			return null;
//		}
//	}
//	Earlier we used to parse statements but after implementing variable declaration we have to check if the declaration is variable
//	declaration or a statement declaration
	List<Stmt> parse(){
		List<Stmt> statements=new ArrayList<>();
		while(!isAtEnd()) {
			statements.add(declaration());
		}
		return statements;
	}
	private Stmt declaration() {
		try {
			if(match(CLASS)) return classDeclaration();
			if(match(FUN)) return function("function");
			if(match(VAR)) return varDeclaration();
			
			return statement();
		}catch(ParserError error) {
			synchronize();
			return  null;
		}
	}
	private Stmt classDeclaration() {
		Token name=consume(IDENTIFIER,"Expect class name");
		
		Expr.Variable superclass=null;
		if(match(LESS)) {
			consume(IDENTIFIER,"Expect superclass name.");
			superclass = new Expr.Variable(previous());
		}
		consume(LEFT_BRACE,"Expect '{' before class body.");
		
		List<Stmt.Function> methods=new ArrayList<>();
		while(!check(RIGHT_BRACE) && !isAtEnd()) {
			methods.add(function("method"));
		}
		
		consume(RIGHT_BRACE,"Expect '}' after class body.");
		
		return new Stmt.Class(name,superclass,methods);
	}
	//Like other statement types adding a method that'll return a function statement
	//the kind parameter is used so that later on when methods are passed into it
	// then we will be able to distinguish it
	private Stmt.Function function(String kind) {
		Token name= consume(IDENTIFIER, "Expect "+ kind +" name.");
		consume(LEFT_PAREN,"Expect '(' after "+ kind + " name.");
		List<Token> parameters = new ArrayList<>();
		if(!check(RIGHT_PAREN)) {
			do {
				if(parameters.size()>=255) {
					error(peek(),"Can't have more than 255 parameters.");
				}
				parameters.add(consume(IDENTIFIER, "Expect parameter name."));
			}while(match(COMMA));
		}
		consume(RIGHT_PAREN, "Expect ')' after parameters.");
		
		consume(LEFT_BRACE,"Expect '{' before "+ kind +" body.");
		List<Stmt> body=block();
		return new Stmt.Function(name,parameters,body);
	}
	//Variable expression accesses binding by looking up to the name and return it's value.
	private Stmt varDeclaration() {
		Token name=consume(IDENTIFIER,"Expect a variable name");
		Expr initializer=null;
		if(match(EQUAL)) {
			initializer=expression();
		}
		consume(SEMICOLON,"Expect ';' after variable declaration.");
		return new Stmt.Var(name, initializer);
	}
	private Expr expression() {
		return assignment();
	}
	//We would return an error if the r-value seems to be mutating.
	//we assign expr to equality and if we get =, then we evaluate r-value and re-bind to l-value.
	private Expr assignment() {
		Expr expr=or();
		
		if(match(EQUAL)) {
			Token equals=previous();
			Expr value=assignment();
			
			if(expr instanceof Expr.Variable) {
				Token name= ((Expr.Variable)expr).name;
				return new Expr.Assign(name, value);
			}else if (expr instanceof Expr.Get) {
				Expr.Get get = (Expr.Get)expr;
				return new Expr.Set(get.object,get.name,value);
			}
			
			error(equals,"Invalid assignment Target.");
		}
		
		return expr;
	}
	//these two statements are disjoints so there's no need to keep them together
	//splitting expressions and statements into separate class hierarchies enables the Java
	//compiler to help us find dumb mistakes like passing a statement to java method that
	//expects a expression
	//If the next token doesn't look like any known kind of statement,we assume it
	//must be an expression statement.
	private Stmt statement() {
		if(match(PRINT)) return printStatement();
		if(match(IF)) return ifStatement();
		if(match(RETURN)) return returnStatement();
		if(match(WHILE)) return whileStatement();
		if(match(FOR)) return forStatement();
		if(match(LEFT_BRACE)) return new Stmt.Block(block());
		return expressionStatement();
	}
	private Stmt returnStatement() {
		Token keyword=previous();
		Expr value=null;
		if(!check(SEMICOLON)) {
			value=expression();
		}
		
		consume(SEMICOLON, "Expect ';' after return value.");
		return new Stmt.Return(keyword, value);
	}
	private Stmt ifStatement() {
		consume(LEFT_PAREN,"Expect '(' after 'if'.");
		Expr condition=expression();
		consume(RIGHT_PAREN,"Expect ')' after if condition");
		
		Stmt thenBranch=statement();
		Stmt elseBranch=null;
		if(match(ELSE)) {
			elseBranch=statement();
		}
		return new Stmt.If(condition, thenBranch, elseBranch);
	}
	private Stmt whileStatement() {
		consume(LEFT_PAREN,"Expect '(' after 'if'.");
		Expr condition=expression();
		consume(RIGHT_PAREN,"Expect ')' after if condition");
		
		Stmt body=statement();
		
		return new Stmt.While(condition, body);
	}
	private Stmt forStatement() {
		consume(LEFT_PAREN,"Expect '(' after 'for'.");
		Stmt initializer;
		if(match(SEMICOLON)) {
			initializer=null;
		} else if(match(VAR)) {
			initializer=varDeclaration();
		}else {
			initializer=expressionStatement();
		}
		Expr condition=null;
		if(!check(SEMICOLON)) {
			condition=expression();
		}
		consume(SEMICOLON,"Expect ';' after loop condition.");
		Expr increment=null;
		if(!check(RIGHT_PAREN)) {
			increment=expression();
		}
		consume(RIGHT_PAREN,"Expect ')' after for clauses.");
		Stmt body=statement();
		
		
		if(increment!=null) {
			body=new Stmt.Block(Arrays.asList(body, new Stmt.Expression(increment)));
		}
		if(condition==null) condition=new Expr.Literal(true);
		body=new Stmt.While(condition, body);
		if(initializer !=null) {
			body=new Stmt.Block(Arrays.asList(initializer, body));
		}
		return body;
	}
	private List<Stmt> block() {
		List<Stmt> statements=new ArrayList<>();
		
		while (!check(RIGHT_BRACE) && !isAtEnd()) {
			statements.add(declaration());
		}
		
		consume(RIGHT_BRACE,"Expect '}' after block.");
		return statements;
	}
	//we parse the expression and consume the subsequent semicolon symbol and emit the 
	//syntax tree.
	private Stmt printStatement() {
		Expr value=expression();
		consume(SEMICOLON,"Expect ';' after value.");
		return new Stmt.Print(value);
	}
	private Stmt expressionStatement() {
		Expr value=expression();
		consume(SEMICOLON,"Expect ';' after value.");
		return new Stmt.Expression(value);
	}
	private Expr equality() {
		Expr expr=comparison();
		while(match(BANG_EQUAL,EQUAL_EQUAL)){
			Token operator=previous();
			Expr right=comparison();
			expr=new Expr.Binary(expr, operator, right);
		}
		
		return expr;
	}
	private Expr comparison() {
		Expr expr=term();
		while(match(GREATER,GREATER_EQUAL,LESS,LESS_EQUAL)) {
			Token operator=previous();
			Expr right=term();
			expr=new Expr.Binary(expr, operator, right);
		}
		return expr;
	}
	private Expr term() {
		Expr expr=factor();
		while(match(MINUS,PLUS)) {
			Token operator=previous();
			Expr right=factor();
			expr=new Expr.Binary(expr, operator, right);
		}
		return expr;
	}
	private Expr factor() {
		Expr expr=unary();
		while(match(SLASH,STAR)) {
			Token operator=previous();
			Expr right=unary();
			expr=new Expr.Binary(expr, operator, right);
		}
		return expr;
	}
	private Expr or() {
		Expr expr=and();
		
		while(match(OR)) {
			Token operator=previous();
			Expr right= and();
			expr= new Expr.Logical(expr,operator,right);
			}
		return expr;
	}
	private Expr and() {
		Expr expr=equality();
		while(match(AND)) {
			Token operator=previous();
			Expr right= and();
			expr= new Expr.Logical(expr,operator,right);
			}
		return expr;
	}
	private Expr unary() {
		if(match(BANG,MINUS)) {
			Token operator=previous();
			Expr right=primary();
			return new Expr.Unary(operator,right);
		}
		return calle();
	}
	private Expr calle() {
		Expr expr=primary();
		
		while(true) {
			if(match(LEFT_PAREN)) {
				expr=finishCall(expr);
			}else if(match(DOT)){
				Token name=consume(IDENTIFIER,"Expect property name after '.'.");
				expr = new Expr.Get(expr, name);
			}else {
				break;
			}
		}
		
		return expr;
	}
	private Expr finishCall(Expr callee) {
		List<Expr> arguments=new ArrayList<>();
		if(!check(RIGHT_PAREN)) {
			do {
				if(arguments.size()>=255) {
					error(peek(),"Can't have more than 255 arguements.");
				}
				arguments.add(expression());
			} while(match(COMMA));
		}
		Token paren=consume(RIGHT_PAREN,"Expect ')' after arguments");
		
		return new Expr.Call(callee, paren, arguments);
	}
	private Expr primary() {
		  if(match(FALSE)) return new Expr.Literal(FALSE);
		  if(match(TRUE)) return new Expr.Literal(TRUE);
		  if(match(NIL)) return new Expr.Literal(NIL);
		  
		  if(match(NUMBER,STRING)) {
			  return new Expr.Literal(previous().literal);
		  }
		  if(match(SUPER)) {
			  Token keyword=previous();
			  consume(DOT,"Expect '.' after 'super'.");
			  Token method=consume(IDENTIFIER,"Expect superclass method name.");
			  return new Expr.Super(keyword, method);
		  }
		  if(match(THIS)) return new Expr.This(previous());
		  if(match(IDENTIFIER)) {
			  return new Expr.Variable(previous());
		  }
		  
		  if(match(LEFT_PAREN)) {
			  Expr expr=expression();
			  consume(RIGHT_PAREN,"Expect ')' after expression.");
			  return new Expr.Grouping(expr);
		  }
		throw error(peek(),"Expect expression.");
	}
	private Token consume(TokenType rightParen,String message) {
		if(check(rightParen)) return advance();
		
		throw error(peek(),message);
	}
	private ParserError error(Token token,String message) {
		Lox.error(token, message);
		return new ParserError();
	}
	private void synchronize() {
		advance();
		while(!isAtEnd()) {
			if(previous().type==SEMICOLON) return;
			
			switch(peek().type) {
			case CLASS:
			case FUN:
			case VAR:
			case FOR:
			case IF:
			case WHILE:
			case PRINT:
			case RETURN:
				return;
			}
			advance();
		}
	}
	private boolean match(TokenType... types) {
		for(TokenType type:types) {
			if(check(type)) {
				advance();
				return true;
			}
		}
		return false;
	}
	private boolean check(TokenType type) {
		if(isAtEnd()) return false;
		return peek().type==type;
	}
	private Token advance() {
		if(!isAtEnd()) current++;
		return previous();
	}
	private boolean isAtEnd() {
		return peek().type==EOF;
	}
	private Token peek() {
		return tokens.get(current);
	}
	private Token previous() {
		return tokens.get(current-1);
	}
}