package com.craftinginterpreters.jlox;

abstract class ExpressionSample {
	static class Binary extends ExpressionSample{
		Binary(ExpressionSample left,Token operator,ExpressionSample right){
			this.left=left;
			this.operator=operator;
			this.right=right;
		}
		final ExpressionSample left;
		final ExpressionSample right;
		final Token operator;
	}
}