package com.botifier.database.main;

import java.io.IOException;

import com.botifier.database.Database;

public class Main {
	
	public static void main(String[] args) {
		System.out.println("Starting GameItemDatabase...");
		Database db = new Database();
		db.start();
		db.stop();
	}
	
}
