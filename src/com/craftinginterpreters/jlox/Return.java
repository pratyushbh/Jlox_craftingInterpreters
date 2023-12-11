package com.craftinginterpreters.jlox;
class Returnval extends RuntimeException {
	final Object value;
	
	Returnval(Object value){
		super(null,null,false,false);
		this.value=value;
	}
}