package com.botifier.database.main;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Properties;
import java.util.UUID;

public class Config {

	public static final String USERNAME_PLACEHOLDER = "\\{UN}";
	
	public static final String DEFAULT_USER = "vd";
	
	public static final UUID DEFAULT_UUID = new UUID(0,0);
	
	public static final String DEFAULT_FOLDER = "GameItemDatabase/";
	
	public static final String DEFAULT_USER_FOLDER = "\\{UN}/";
	
	public static final String DEFAULT_ITEM_FILENAME = "\\{UN}-items";	
	
	public static final long DEFAULT_ITEM_LIMIT = 10000;
	
	public static final boolean DEFAULT_USE_MONGO = false;
	
	public static final String DEFAULT_MONGO_DATABASE_NAME = "GameItemDatabase";
	
	public static final String DEFAULT_MONGO_ITEM_COLLECTION_NAME = "Items";
	
	public static final String DEFAULT_MONGO_USER_COLLECTION_NAME = "Users";
	
	public static final String DEFAULT_MONGO_IP = "localhost";
	
	public static final int DEFAULT_MONGO_PORT = 27017;
	
	public static String databaseFolder = DEFAULT_FOLDER;
	
	public static long item_limit = DEFAULT_ITEM_LIMIT;
	
	public static boolean useMongo = DEFAULT_USE_MONGO;
	
	public static int mongoPort = DEFAULT_MONGO_PORT;
	
	public static String mongoIp = DEFAULT_MONGO_IP;
	
	public static String mongoDBName = DEFAULT_MONGO_DATABASE_NAME;
	
	public static String mongoItemCol = DEFAULT_MONGO_ITEM_COLLECTION_NAME;
	
	public static String mongoUserCol = DEFAULT_MONGO_USER_COLLECTION_NAME;
	
	/**
	 * Generates a brand new config
	 * @throws IOException
	 */
	public static void generateNewConfig() throws IOException {
		//Make this dynamic to allow for the addition of more config options without modifying this function. 
		System.out.println("Creating Config...");
		File f = new File("config.cfg");
		BufferedWriter bw = new BufferedWriter(new FileWriter(f));

		bw.write("database_folder="+ DEFAULT_FOLDER+"\n");
		bw.write("user_item_limit="+DEFAULT_ITEM_LIMIT+"\n");
		bw.write("useMongo="+DEFAULT_USE_MONGO+"\n");
		bw.write("mongoIP=" + DEFAULT_MONGO_IP+"\n");
		bw.write("mongoPort="+DEFAULT_MONGO_PORT+"\n");
		bw.write("mongoDBName=" + DEFAULT_MONGO_DATABASE_NAME+"\n");
		bw.write("mongoUserCol=" + DEFAULT_MONGO_USER_COLLECTION_NAME+"\n");
		bw.write("mongoItemCol="+DEFAULT_MONGO_ITEM_COLLECTION_NAME+"\n");
		bw.close();
		System.out.println("Done!");
	}
	
	/**
	 * Loads config from config.cfg
	 * @throws IOException
	 */
	public static void loadConfig() throws IOException {
		System.out.println("Loading Config...");
		File f = new File("config.cfg");
		if (!f.exists()) {
			System.out.println("Config does not exist.");
			generateNewConfig();
		}
		System.out.println("Reading Config...");
		Reader r = new InputStreamReader(new FileInputStream(f));
		Properties p = new Properties();
		p.load(r);
		System.out.println("Setting Variables...");
		databaseFolder = p.getProperty("database_folder");
		item_limit = Long.valueOf(p.getProperty("user_item_limit"));
		useMongo = Boolean.valueOf(p.getProperty("useMongo"));
		mongoPort = Integer.valueOf(p.getProperty("mongoPort"));
		mongoIp = p.getProperty("mongoIP");
		mongoDBName = p.getProperty("mongoDBName");
		mongoItemCol = p.getProperty("mongoItemCol");
		mongoUserCol = p.getProperty("mongoUserCol");
		
		r.close();
		System.out.println("Done!");
	}

}
