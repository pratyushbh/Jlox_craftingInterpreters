package com.craftinginterpreters.jlox;

import java.util.HashMap;
import java.util.Map;

//Environment is basically a data structure, that bounds the variable to a context.
//fun fact: before lisp, parenthesis and environment was considered the same thing.
class Environment{
	final Environment enclosing;
	private final Map<String, Object> values=new HashMap<>();
	Environment(){
		enclosing=null;
	}
	Environment(Environment enclosing){
		this.enclosing=enclosing;
	}
	//The map uses name as key instead of Token to represent binded value, because token is a unit of code at a specific place in source text.
	//but when it comes to looking up variables, all identifier tokens with same name should refer to the same variable. Using raw string we 
	//could do that.
	//In our case if variable name is already in use, and we assign new value to it using var, it wont be added to new memory instead it will be
	//reassigned.
	//If we would've assigned the error arising here as syntax error then recursion would've been a real big headache.
	Object get(Token name) {
		if(values.containsKey(name.lexeme)) {
			return values.get(name.lexeme);
		}
		if(enclosing!=null) return enclosing.get(name);
		throw new RuntimeError(name, "Undefined variable '"+name.lexeme+"'.");
	}
	
	void define(String name,Object value) {
		values.put(name, value);
	}
	//We throw runtime error we try to assign value to key that doesn't exist.
	void assign(Token name,Object value) {
		if(values.containsKey(name.lexeme)) {
			values.put(name.lexeme, value);
			return;
		}
		if(enclosing!=null) {
			enclosing.assign(name, value);
			return;
		}
		throw new RuntimeError(name,"Undefined variable '"+name.lexeme+"'.");
	}
	Object getAt(int distance,String name) {
		return ancestor(distance).values.get(name);
	}
	Environment ancestor(int distance) {
		Environment environment=this;
		for(int i=0;i<distance;i++) {
			environment=environment.enclosing;
		}
		return environment;
	}
	void assignAt(int distance,Token name,Object value) {
		ancestor(distance).values.put(name.lexeme, value);
	}
}