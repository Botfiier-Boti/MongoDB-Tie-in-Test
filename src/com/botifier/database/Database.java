package com.botifier.database;

import static com.mongodb.client.model.Filters.*;
import static com.botifier.database.main.Config.*;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Serializable;
import java.lang.reflect.Type;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;

import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.json.JsonObject;

import com.botifier.database.main.Config;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.FindIterable;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;



public class Database implements Serializable{
	/**
	 * 
	 */
	private static final long serialVersionUID = 1595287555711769296L;
	
	//User HashMaps, tied together to aid with searching based on either name or UUID
	private HashMap<String, UUID> allUsers = new HashMap<String, UUID>();
	private HashMap<UUID, DatabaseUser> users = new HashMap<UUID, DatabaseUser>();
	
	//Item HashMap
	private HashMap<UUID, DatabaseObject<?>> items = new HashMap<UUID, DatabaseObject<?>>();

	//Gson
	private Gson gson = null;
	
	/**
	 * Starts the program
	 */
	public void start() {
		

		try {
			Config.loadConfig();
		} catch (IOException e) {
			System.out.println("Config load failed.");
			e.printStackTrace();
		} 

		GsonBuilder gb = new GsonBuilder();
		gb.registerTypeHierarchyAdapter(DatabaseObject.class, new DatabaseObjectAdapter(this));
		gb.setPrettyPrinting();
		gson = gb.create();

		try {
			load();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		DatabaseObject<?>[] d = items.values().toArray(new DatabaseObject<?>[0]);
		for (int e = d.length-1; e >= 0; e--) {
			DatabaseObject<?> i = d[e];
			if (i.getOwner() == null) {
				for (Entry<UUID, DatabaseObject<?>> entry : items.entrySet()) {
					if (i.equals(entry.getValue())) {
						System.out.println("Removed ownerless item of UUID: "+entry.getKey());
						items.remove(entry.getKey());
						break;
					}
				}
				continue;
			}
			//System.out.println(i);
		}
	}
	
	/**
	 * Stops the program
	 */
	public void stop() {
		try {
			write();
		} catch (IOException e) {
			System.out.println("Write failed");
			e.printStackTrace();
		}
	}
	
	/**
	 * Checks if a user is loaded using their name
	 * @param name String To use
	 * @return boolean
	 */
	public boolean userLoaded(String name) {
		if (useMongo)
			return false;
		for (DatabaseUser u : users.values()) {
			if (u.getName().equalsIgnoreCase(name))
				return true;
		}
		return false;
	}
	
	/**
	 * Checks if a user is loaded using their UUID
	 * @param u UUID To use
	 * @return boolean
	 */
	public boolean userLoaded(UUID u) {
		if (useMongo)
			return false;
		return users.containsKey(u);
	}
	
	/**
	 * Creates a brand new user using name
	 * @param name String to Use
	 * @return DatabaseUser
	 */
	public DatabaseUser createUser(String name) {
		if (allUsers.containsKey(name))
			return null;
		if (!verifyNewUser(name, null)) {
			return null;
		}
		DatabaseUser temp = new DatabaseUser(name);
		if (useMongo) {
			writeUserMongo(temp);
		} else {
			users.put(temp.getUUID(), temp);
			allUsers.put(name, temp.getUUID());
		}
		return temp;
	}
	
	/**
	 * Creates a user using name and uuid
	 * Used for loading pre-existing users
	 * @param name String To use
	 * @param uuid UUID To use
	 * @return DatabaseUser
	 */
	private DatabaseUser createUser(String name, UUID uuid) {
		DatabaseUser temp = new DatabaseUser(name, uuid);
		users.put(temp.getUUID(), temp);
		allUsers.put(name, temp.getUUID());
		return temp;
	}
	
	public boolean verifyNewUser(String name, UUID u) {
		if (users.containsKey(u))
			return false;
		if (allUsers.containsKey(name))
			return false;
		if (useMongo) {
			try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
				MongoDatabase database = mClient.getDatabase(mongoDBName);
				MongoCollection<Document> item = database.getCollection(mongoUserCol);
				
				Bson filter = or(eq("_id", u), eq("name", name));

				Document d = item.find(filter).first();
				
				if (d ==null || d.isEmpty())
					return true;
			}
		}
		return true;
	}
	
	/**
	 * Finds a user based on their name
	 * @param name String To search
	 * @return DatabaseUser
	 */
	public DatabaseUser getUser(String name) {
		if (useMongo)
			return getUserMongo(name);
		UUID uuid = allUsers.get(name);
		if (!users.containsKey(uuid))
			return createUser(name, uuid);
		return users.get(uuid);
	}
	
	/**
	 * Finds a user based on their UUID
	 * @param u UUID To search
	 * @return DatabaseUser
	 */
	public DatabaseUser getUser(UUID u) {
		if (useMongo)
			return getUserMongo(u);
		if (!users.containsKey(u))
			if (allUsers.containsValue(u)) {
				StringBuilder sb = new StringBuilder();
				sb.append(allUsers.entrySet().stream()
						.filter(entry -> u.equals(entry.getValue()))
						.map(Map.Entry::getKey));
				getUser(sb.toString());
			}
		return users.get(u);
	}
	
	/**
	 * Creates a DatabaseObject with information of type T and stores it in the items HashMap
	 * Used the create brand new DatabaseObjects
	 * @param <T> Type of information
	 * @param info T Information to store
	 * @param owner DatabaseUser the item's owner
	 * @return
	 */
	public <T extends Object> DatabaseObject<T> createItem(T info, DatabaseUser owner) {
		if (info == null)
			throw new NullPointerException();
		DatabaseObject<T> obj = new DatabaseObject<T>(info, owner);
		obj.manager = this;
		items.put(obj.getUUID(), obj);
		return obj;
	}
	
	/**
	 * Creates a DatabaseObject with information of type T and stores it in the items HashMap
	 * Used for creating DatabaseObjects from deserialization
	 * @param <T> Type of information
	 * @param info T Information to store
	 * @param u UUID uuid to use
	 * @param owner DatabaseUser the item's owner
	 * @param last_modification long Timestamp of last modification
	 * @return
	 */
	protected <T extends Object> DatabaseObject<T> createItem(T info, UUID u, DatabaseUser owner, long last_modification) {
		if (info == null || u == null)
			throw new NullPointerException();
		DatabaseObject<T> obj = new DatabaseObject<T>(info, u, owner);
		obj.manager = this;
		obj.last_modification = last_modification;
		items.put(obj.getUUID(), obj);
		return obj;
	}
	
	/**
	 * Gets a specific object using MongoDB
	 * @param u UUID To search
	 * @return DatabaseObject<?>
	 */
	public DatabaseObject<?> getObjectMongo(UUID u) {
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<JsonObject> item = database.getCollection(mongoItemCol, JsonObject.class);
			
			Bson filter = eq("_id", u.toString());
			
			JsonObject jo = item.find(filter).first();
			Type ty = new TypeToken<DatabaseObject<?>>() {}.getType();
			DatabaseObject<?> d = gson.fromJson(jo.getJson(), ty);
			
			return d;
		}
	}
	
	/**
	 * Gets a specific user using MongoDB
	 * @param u UUID To search
	 * @return DatabaseUser
	 */
	public DatabaseUser getUserMongo(UUID u) {
		if (users.containsKey(u))
			return users.get(u);
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<Document> item = database.getCollection(mongoUserCol);
			
			Bson filter = eq("_id", u);

			Document d = item.find(filter).first();
			if (d == null)
				return null;
			String name = d.getString("name");
			if (name == null || name.isEmpty())
				return null;
			return createUser(name, u);
		}
	}
	
	/**
	 * Gets a specific user using MongoDB
	 * @param s String To search
	 * @return DatabaseUser
	 */
	public DatabaseUser getUserMongo(String s) {
		if (allUsers.containsKey(s))
			return users.get(allUsers.get(s));
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<Document> item = database.getCollection(mongoUserCol);
			
			Bson filter = eq("name", s);

			Document d = item.find(filter).first();
			if (d == null)
				return null;
			String id = d.getString("_id");
			if (id == null || id.isEmpty())
				return null;
			UUID u = UUID.fromString(id);
			return createUser(s, u);
		}
	}
	
	/**
	 * Loads all items and users
	 * @throws IOException
	 */
	public void load() throws IOException {

		System.out.println("Load Started");
		if (useMongo == true) {
			//loadUsersMongo();
			
			//Adds the default user if the allUsers map is empty
			if (allUsers.isEmpty()) {
				System.out.println("Adding default user.");
				createUser(DEFAULT_USER, DEFAULT_UUID);
			}
			
			//loadItemsMongo();
		}  else {
			File uDir = new File(DEFAULT_FOLDER+"/users/");
			
			if (uDir.exists()) {
				loadUsersFlatFile(uDir);
			}

			if (allUsers.isEmpty()) {
				System.out.println("Map is empty adding default user.");
				createUser(DEFAULT_USER, DEFAULT_UUID);
			}
			
			File iFile = new File(DEFAULT_FOLDER+"/items/items.json");
			if (iFile.exists()) {
				loadItemsFlatFile(new InputStreamReader(new FileInputStream(iFile)));
			}
		}


	}
	
	/**
	 * Loads users from a flatfile and puts them in the allUsers and users HashMaps
	 * @param uDir File to Use
	 * @throws IOException
	 */
	public void loadUsersFlatFile(File uDir) throws IOException {
		allUsers.clear();
		users.clear();
		File[] users = uDir.listFiles(File::isDirectory);
		for (File f : users) {
			String name = f.getName();
			File uInfo = new File(f.getPath()+"/userinfo");
			if (!uInfo.exists())
				continue;
			try (Reader r = new InputStreamReader(new FileInputStream(uInfo))) {
				Properties p = new Properties();
				p.load(r);
				
				String uuidProp = p.getProperty("uuid");
				if (uuidProp == null || uuidProp.isEmpty())
					continue;
				UUID u = UUID.fromString(uuidProp);
				allUsers.put(name, u);
				createUser(name, u);

				System.out.println("Added "+name+" to all user map.");
			}
			
		}
	}
	
	/**
	 * Loads users from MongoDB and stores them in the allUsers and users HashMaps
	 * @throws UnknownHostException
	 */
	public void loadUsersMongo() {
		allUsers.clear();
		users.clear();
		
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<Document> item = database.getCollection(mongoUserCol);
			
			FindIterable<Document> fi = item.find();
			for (Document d : fi) {
				UUID u = UUID.fromString(d.getString("_id"));
				String name = d.getString("name");
				createUser(name, u);
			}
		}
		
	}
	
	/**
	 * Writes a single user to MongoDB
	 * @param dbu DatabaseUser To write
	 */
	public void writeUserMongo(DatabaseUser dbu) {
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<Document> item = database.getCollection(mongoUserCol);
			
			Document d = new Document("_id", dbu.getUUID().toString())
					 .append("name", dbu.getName())
					 .append("last_modified", dbu.last_modification);
			Bson filter = Filters.eq("_id", dbu.getUUID().toString());
			item.replaceOne(filter, d, new ReplaceOptions().upsert(true));
		}
	}
	
	/**
	 * Writes a single object to MongoDB
	 * @param i DatabaseObject To write
	 */
	public void writeItemMongo(DatabaseObject<?> i) {
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<JsonObject> item = database.getCollection(mongoItemCol, JsonObject.class);
			
			JsonObject temp = new JsonObject(i.toJson());
			
			Bson filter = Filters.eq("_id", i.getUUID().toString());
			item.replaceOne(filter, temp, new ReplaceOptions().upsert(true));
		}
	}
	
	/**
	 * Loads all of the items from MongoDB and deserializes them.
	 * @throws UnknownHostException
	 */
	public void loadItemsMongo() {
		items.clear();
		
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<JsonObject> item = database.getCollection(mongoItemCol, JsonObject.class);
			
			FindIterable<JsonObject> fi = item.find();
			for (JsonObject jo : fi) {
				Type ty = new TypeToken<DatabaseObject<?>>() {}.getType();
				DatabaseObject<?> d = gson.fromJson(jo.getJson(), ty);
				d.manager = this;
				items.put(d.getUUID(), d);
				d.getOwner().addObject(d);
			}
		}
	}
	
	/**
	 * Deserializes items from a flatfile and puts them into the item HashMap
	 * @param r Reader To use
	 * @throws IOException
	 */
	public void loadItemsFlatFile(Reader r) throws IOException {
		items.clear();
		
		Type doT = new TypeToken<HashMap<UUID, DatabaseObject<?>>>() {}.getType();
		HashMap<UUID, DatabaseObject<?>> hold = gson.fromJson(r, doT);
		if (hold != null)
			items = hold;
		r.close();
		
	}
	
	/**
	 * Writes information to either MongoDB or a Flatfile depending on useMongo
	 * @throws IOException
	 */
	public void write() throws IOException {
		if (useMongo == true) {
			writeUsersMongo();
			writeItemsMongo();

			users.clear();
			allUsers.clear();
			items.clear();
		} else {
			writeUsersFlatFile();
			writeItemsFlatFile();
		}
	}
	
	/**
	 * Writes the user HashMap to a flatfile
	 * @throws IOException
	 */
	public void writeUsersFlatFile() throws IOException {
		File uDir = new File(DEFAULT_FOLDER+"/users/");
		uDir.mkdirs();
		for (DatabaseUser dbu : users.values()) {
			if (dbu == null)
				return;
			String fName = DEFAULT_FOLDER+"/users/"+DEFAULT_USER_FOLDER;
			fName = fName.replaceAll(USERNAME_PLACEHOLDER, dbu.getName());
			
			
			File uuDir = new File(fName);
			uuDir.mkdir();
			
			File f = new File(fName+"userinfo");
			BufferedWriter bw = new BufferedWriter(new FileWriter(f));

			bw.write("uuid="+dbu.getUUID().toString()+"\n");
			bw.write("last_modified="+dbu.getLastModified()+"\n");
			bw.close();
			
			/*File f2 = new File(fName+"items");
			bw = new BufferedWriter(new FileWriter(f2));

			for (UUID uuid : dbu.getOwnedItems()) {
				bw.write(uuid+"\n");
			}
			bw.close();*/
			
		}
	}
	
	/**
	 * Writes the item HashMap to a flatfile
	 * @throws IOException
	 */
	public void writeItemsFlatFile() throws IOException {
		File iDir = new File(DEFAULT_FOLDER+"/items/");
		iDir.mkdir();

		BufferedWriter bw = new BufferedWriter(new FileWriter(new File(iDir.getPath()+"/items.json")));
		String json = gson.toJson(items);
		bw.write(json);

		bw.close();
	}
	
	/**
	 * Writes the item HashMap to the MongoDB specified in Config
	 * @throws UnknownHostException
	 */
	public void writeItemsMongo() {
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<JsonObject> item = database.getCollection(mongoItemCol, JsonObject.class);
			
			
			DatabaseObject<?>[] d = items.values().toArray(new DatabaseObject<?>[0]);
			for (int e = d.length-1; e >= 0; e--) {
				DatabaseObject<?> i = d[e];
				JsonObject temp = new JsonObject(i.toJson());
				
				Bson filter = Filters.eq("_id", i.getUUID().toString());
				item.replaceOne(filter, temp, new ReplaceOptions().upsert(true));
			}
		}
	}
	
	/**
	 * Writes the user HashMap to the MongoDB specified in Config
	 * @throws UnknownHostException
	 */
	public void writeUsersMongo() {
		try (MongoClient mClient = MongoClients.create("mongodb://"+mongoIp +":" + mongoPort)) {
			MongoDatabase database = mClient.getDatabase(mongoDBName);
			MongoCollection<Document> item = database.getCollection(mongoUserCol);
			
			for (DatabaseUser dbu : users.values()) {
				Document d = new Document("_id", dbu.getUUID().toString())
								 .append("name", dbu.getName())
								 .append("last_modified", dbu.last_modification);
				Bson filter = Filters.eq("_id", dbu.getUUID().toString());
				item.replaceOne(filter, d, new ReplaceOptions().upsert(true));
			}
		}
	}
	
	/**
	 * Returns all of the loaded items
	 * @return HashMap<UUID, DatabaseObject> Loaded items
	 */
	public HashMap<UUID, DatabaseObject<?>> getLoadedItems() {
		return items;
	}
	
	public static class DatabaseObject<T extends Object> implements Comparable<DatabaseObject<T>> {
		//The database this item exists in
		private Database manager;
		
		//The current owner of this object
		private DatabaseUser owner;
		
		//Information stored within this object
		private T information;
		
		//The objects UUID
		private UUID uuid;
		
		//The last time this object was modified
		private long last_modification = 0;
		
		//Destruction flag
		private boolean exists = true;
		
		/**
		 * DatabaseObject constructor
		 * Used when making new objects
		 * @param information T information to hold
		 */
		private DatabaseObject(T information, DatabaseUser owner) {
			init(information, UUID.randomUUID(), owner);
			this.last_modification = System.currentTimeMillis();
		}
		
		/**
		 * DatabaseObject constructor
		 * Used when loading objects
		 * @param information T information to hold
		 * @param uuid UUID uuid of object
		 */
		private DatabaseObject(T information, UUID uuid, DatabaseUser owner) {
			init(information, uuid, owner);
		}
		
		private void init(T information, UUID uuid, DatabaseUser owner) {
			this.information = information;
			this.uuid = uuid;
			this.owner = owner;
			if (owner != null)
				owner.addObject(this);
		}
		
		/**
		 * Changes the stored information in this object
		 * @param information T
		 */
		public void putInformation(T information) {
			this.last_modification = System.currentTimeMillis();
			this.information = information;
		}
		
		/**
		 * Turns on the destroy flag so that the object can be destroyed.
		 */
		public void destroy() {
			this.last_modification = System.currentTimeMillis();
			exists = false;
		}

		/**
		 * Returns the UUID of this object
		 * @return UUID
		 */
		public UUID getUUID() {
			return uuid;
		}
		
		/**
		 * Returns the current owner of this object
		 * @return DatabaseUser
		 */
		public DatabaseUser getOwner() {
			return owner;
		}
		
		/**
		 * Returns the information held within this object
		 * @return T
		 */
		public T getInformation() {
			return information;
		}
		
		/**
		 * Returns the last time this object was modified
		 * @return long
		 */
		public long getLastModified() {
			return last_modification;
		}
		
		/**
		 * Returns whether or not the object still exists
		 * @return boolean
		 */
		public boolean exists() {
			return exists;
		}
		
		/**
		 * Uses Gson in order to serialize object into JSON format.
		 * @return String JSON string
		 */
		public String toJson() {
			GsonBuilder gb = new GsonBuilder();
			gb.registerTypeHierarchyAdapter(DatabaseObject.class, new DatabaseObjectAdapter(manager));
			Gson gson = gb.create();
			
			return gson.toJson(this);
		}
		
		@Override
		public String toString() {
			String s = "Object\n";
			s += "UUID: "+uuid+"\n";
			s += "Information: {"+information.toString()+"}\n";
			s += "Information Type: "+information.getClass()+"\n";
			s += "Owner: "+owner.getName()+"\n";
			return s;
		}
		
		@Override
		public int compareTo(DatabaseObject<T> other) {
			int result = uuid.compareTo(other.getUUID());
			//Triggers the destroy flag if it matches another object of the same type's uuid, as one is a duplicate.
			if (result == 0) {
				destroy();
			}
			return result;
		}
		
		
	}
	
	public static class DatabaseUser implements Serializable {
		/**
		 * 
		 */
		private static final long serialVersionUID = -3101083165791268022L;

		//The user's name
		private String name;
		
		//The user's UUID
		private UUID uuid;
		
		//The objects that belong to a user
		private HashMap<UUID, DatabaseObject<?>> objects = new HashMap<UUID, DatabaseObject<?>>();
		
		//The last time this user was modified
		private long last_modification = 0;
		
		
		/**
		 * DatabaseUser constructor
		 * Creates a brand new user and generates a new UUID
		 * @param name
		 */
		private DatabaseUser(String name) {
			this.name = name;
			this.uuid = UUID.randomUUID();
			this.last_modification = System.currentTimeMillis();
		}
		
		/**
		 * DatabaseUser constructor
		 * Creates an user using a name and a UUID
		 * Used for loading users
		 * @param name
		 * @param uuid
		 */
		private DatabaseUser(String name, UUID uuid) {
			this.name = name;
			this.uuid = uuid;
		}
		
		/**
		 * Adds an object to the user's collection
		 * @param object DatabaseObject<?>
		 */
		public boolean addObject(DatabaseObject<?> object) {
			//Makes sure the object isn't null
			if (object == null)
				return false;
			//Don't add the object if it should be destroyed
			if (!object.exists()) 
				return false;
			//Don't add item if it would go over the item limit
			if (objects.size() + 1 > item_limit)
				return false;
			last_modification = System.currentTimeMillis();
			//Checks if user already has object with the same uuid
			if (hasObject(object.getUUID())) {
				//If so makes sure it is not trying to put the object it already has into itself
				if (!objects.get(object.getUUID()).equals(object)) {
					//Destroys this instance of the object if it is not the same.
					object.destroy();
				}
				return false;
			}
			//Places the object with its uuid in the map
			objects.put(object.getUUID(), object);
			return true;
		}
		
		/**
		 * Removes an object from the user's collection
		 * @param object DatabaseObject<?>
		 */
		public boolean removeObject(DatabaseObject<?> object) {
			if (object == null)
				return false;
			last_modification = System.currentTimeMillis();
			object.destroy();
			objects.remove(object.getUUID());
			return true;
		}
		
		/**
		 * Transfers an object from this user to another
		 * @param target DatabaseUser
		 * @param o DatabaseObject<?>
		 * @return
		 */
		public boolean transferObject(DatabaseUser target, DatabaseObject<?> o) {
			//Don't attempt to send an object the user doesn't own
			if (!hasObject(o.getUUID())) {
				return false;
			}
			//Don't attempt to give someone a duplicate of an object they own
			if (target.hasObject(o.getUUID())) {
				return false;
			}

			o.owner = target;
			target.addObject(o);
			last_modification = System.currentTimeMillis();
			objects.remove(o.getUUID());
			
			return true;
		}
		
		public Set<UUID> getOwnedItems() {
			return objects.keySet();
		}
		
		/**
		 * Checks if the user's object map contains specified object using UUID
		 * @param u UUID to Use
		 * @return boolean
		 */
		public boolean hasObject(UUID u) {
			return objects.containsKey(u);
		}
		
		/**
		 * Returns the last time in milliseconds that this user was modified
		 * @return long
		 */
		public long getLastModified() {
			return last_modification;
		}
		
		/**
		 * Returns the user's name
		 * @return String
		 */
		public String getName() {
			return name;
		}
		
		/**
		 * Returns the user's UUID
		 * @return UUID
		 */
		public UUID getUUID() {
			return uuid;
		}
	}
	
	
}
