package com.botifier.database;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.UUID;

import com.botifier.database.Database.DatabaseObject;
import com.botifier.database.Database.DatabaseUser;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

public class DatabaseObjectAdapter extends TypeAdapter<DatabaseObject<?>> {
	
	//Database to use
	private Database db;
	
	public DatabaseObjectAdapter(Database db) {
		this.db = db;
	}

	@Override
	public void write(JsonWriter out, DatabaseObject<?> value) throws IOException {
		if (value.getUUID() == null)
			return;
		Gson temp = new Gson();
		Type type = new TypeToken<Object>() {}.getType();
		out.beginObject();
		out.name("_id");
		out.value(value.getUUID().toString());
		out.name("info_type");
		out.value(value.getInformation().getClass().getName());
		out.name("info");
		out.value(temp.toJson(value.getInformation(), type));
		out.name("last_modification");
		out.value(value.getLastModified());
		out.name("owner");
		out.value(value.getOwner().getUUID().toString());
		out.endObject();
	}

	@Override
	public DatabaseObject<?> read(JsonReader in) throws IOException {
		Gson gson = new Gson();
		
		String fieldname = "";
		in.beginObject();
		DatabaseObject<?> d = null;
		Type t = null;
		UUID u = null;
		Object information = null;
		UUID owner = null;
		long last_modification = 0L;
		while (in.hasNext()) {
			JsonToken token = in.peek();
			
			if (token.equals(JsonToken.NAME)) {
				fieldname = in.nextName();
			}
			
			switch (fieldname) {
				case "info_type":
					token = in.peek();
					Class<?> c = null;
					try {
						c = Class.forName(in.nextString());
						t = mapInfoType(c);
					} catch (ClassNotFoundException e) {
						System.out.println("ERROR: Type given as info type is invalid.");
						e.printStackTrace();
					}
					break;
				case "info":
					token = in.peek();
					if (t == null) {
						System.out.println("ERROR: Cannot read info before info_type is valid.\n Skipping...");
						in.nextString();
						break;
					}
					information = gson.fromJson(in.nextString(), t);
					break;
				case "uuid":
				case "_id": 
					token = in.peek();
					String s1 = in.nextString();
					if (s1 == null || s1.isEmpty()) {
						System.out.println("ERROR: Cannot load an item without an UUID.");
						break;
					}
					if (db.getLoadedItems().containsKey(UUID.fromString(s1))) {
						System.out.println("ERROR: UUID already used.");
						break;
					}
					u = UUID.fromString(s1);
					break;
				case "owner":
					token = in.peek();
					String s = in.nextString();
					if (s == null || s.isEmpty()) {
						owner = null;
						break;
					}
					owner = UUID.fromString(s);
					break;
				case "last_modification":
					token = in.peek();
					last_modification = in.nextLong();
					break;
			}
		}
		in.endObject();
		
		DatabaseUser o = db.getUser(owner);
		d = db.createItem(information, u, o, last_modification);
		return d;
	}
	
	private <T> Type mapInfoType(Class<T> inner) {
		return TypeToken.get(inner).getType();
	}
	
}