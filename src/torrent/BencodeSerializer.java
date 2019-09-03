package torrent;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.TreeMap;

/** Converts Java data to bencode, and vice versa.
 * Bencode specification supports only integer, byte[] (string), list (array) and dictionary (map). */
final public class BencodeSerializer {
	/** Exports an integer.
	 * @param v The integer
	 * @param out Output stream of serialization
	 * @throws IOException */
	static private void serialize(long v, OutputStream out) throws IOException {
		new BencodeSerializer(out).write(v);
	}
	/** Exports a byte array.
	 * @param v The byte array
	 * @param out Output stream of serialization
	 * @throws IOException */
	static private void serialize(byte[] v, OutputStream out) throws IOException {
		new BencodeSerializer(out).write(v);
	}
	/** Exports a string.
	 * String exported as UTF-8 byte array.
	 * @param v The string
	 * @param out Output stream of serialization
	 * @throws IOException */
	static private void serialize(String v, OutputStream out) throws IOException {
		serialize(v.getBytes(UTF_8), out);
	}
	/** Exports a list.
	 * @param v The list.
	 * @param out Output stream of serialization
	 * @throws IOException */
	static private void serialize(BencodeList v, OutputStream out) throws IOException {
		new BencodeSerializer(out).write(v);
	}
	/** Exports a dictionary.
	 * @param v The dictionary
	 * @param out Output stream of serialization
	 * @throws IOException */
	static public void serialize(BencodeDictionary v, OutputStream out) throws IOException {
		new BencodeSerializer(out).write(v);
	}

	/** Serializer initialization.
	 * @param out Output stream of serialization */
	private BencodeSerializer(OutputStream out) { this.out = out; }

	/** Output stream of serialization. */
	final private OutputStream out;

	/** Exports an integer.
	 * @param v The integer
	 * @return this
	 * @throws IOException */
	public BencodeSerializer write(long v) throws IOException {
		out.write('i'); out.write(Long.toString(v).getBytes()); out.write('e');
		return this;
	}
	/** Exports byte array.
	 * @param v The byte array
	 * @return this
	 * @throws IOException */
	public BencodeSerializer write(byte[] v) throws IOException {
		out.write(Long.toString(v.length).getBytes()); out.write(':'); out.write(v);
		return this;
	}
	/** Exports a string.
	 * @param v The string
	 * @return this
	 * @throws IOException */
	public BencodeSerializer write(String v) throws IOException { return write(v.getBytes(UTF_8)); }
	/** Exports a list.
	 * @param v The list
	 * @return this
	 * @throws IOException */
	public BencodeSerializer write(BencodeList v) throws IOException {
		out.write('l'); v.serialize(this); out.write('e');
		return this;
	}
	/** Exports a dictionary.
	 * @param v The dictionary
	 * @return this
	 * @throws IOException */
	public BencodeSerializer write(BencodeDictionary v) throws IOException {
		Fields fields = new Fields(this);
		out.write('d'); v.serialize(fields); fields.export(); out.write('e');
		return this;
	}

	/** Java object which exports to bencode list. */
	public interface BencodeList {
		/** Converts list elements to bencode.
		 * @param export Bencode serializator, where elements of list exported
		 * @throws IOException */
		void serialize(BencodeSerializer export) throws IOException;
	}

	/** Java object which exports to bencode dictionary. */
	public interface BencodeDictionary {
		/** Converts dictionary elements to bencode.
		 * @param export Bencode fields serializator, where pair key-values of elements of dictionary
		 * exported
		 * @throws IOException */
		void serialize(Fields export) throws IOException;
	}

	/** A mechanism to export fields of dictionary to bencode.
	 * Bencode fields must be sorted by key byte arrays (not strings). */
	static final public class Fields {
		/** Init mechanism.
		 * @param s Active bencode serializator */
		private Fields(BencodeSerializer s) { serializer = s; }

		/** Active bencode serializator */
		final private BencodeSerializer serializer;
		/** A sorted list of key-value pairs of dictionary.
		 * Because value must exported after all pairs arrive, value replaced with a runnable of export. */
		final private TreeMap<byte[], Runnable> fields = new TreeMap<>();

		/** Export fields with pairs key-values of a dictionary.
		 * @throws IOException */
		private void export() throws IOException {
			for (Entry<byte[], Runnable> e : fields.entrySet()) {
				serializer.write(e.getKey());
				e.getValue().run();
			}
		}

		/** A runnable to write value, to bencode output, with delay.
		 * Because fields values must exported after all pairs arrive and sorted, value replaced
		 * with a runnable of export. */
		private interface Runnable {
			/** Execute the runnable.
			 * @throws IOException */
			void run() throws IOException;
		}

		/** Exports a field with integer value.
		 * @param key Field key
		 * @param v Field value */
		public void write(String key, long v) { fields.put(key.getBytes(UTF_8), () -> serializer.write(v)); }
		/** Exports a field with byte array value.
		 * @param key Field key
		 * @param v Field value */
		public void write(String key, byte[] v) { fields.put(key.getBytes(UTF_8), () -> serializer.write(v)); }
		/** Exports a field with string value.
		 * @param key Field key
		 * @param v Field value */
		public void write(String key, String v) { fields.put(key.getBytes(UTF_8), () -> serializer.write(v)); }
		/** Exports a field with list value.
		 * @param key Field key
		 * @param v Field value */
		public void write(String key, BencodeList v) { fields.put(key.getBytes(UTF_8), () -> serializer.write(v)); }
		/** Exports a field with dictionary value.
		 * @param key Field key
		 * @param v Field value */
		public void write(String key, BencodeDictionary v) { fields.put(key.getBytes(UTF_8), () -> serializer.write(v)); }
	}

	// ================= UNSERIALIZE STUFF =========================================================


	/** Change ArrayList<Node> to protect it from out of bounds exception. */
	static private final class Array extends ArrayList<Node> {
		@Override public Node get(int index) {
			return index < this.size() ? super.get(index) : ABSENT_NODE;
		}
	}

	/** A node of data which protect us from bad casts and null pointers when we request data.
	 * Using e.g. node.getField("fieldName").getField("fieldName2").isExist(), we know if the
	 * specific field exists. If field 'fieldName' doesn't exist, there is no NullPointerException.
	 * With that way, we avoid much of checking code. */
	public interface Node {
		/** Node exists.
		 * @return Node exists */
		default boolean isExist() { return true; }
		/** Node is integer.
		 * @return Node is integer */
		default boolean isInteger() { return false; }
		/** Node is byte array.
		 * @return Node is byte array */
		default boolean isByteArray() { return false; }
		/** Node is list.
		 * @return Node is list */
		default boolean isList() { return false; }
		/** Node is dictionary.
		 * @return Node is dictionary */
		default boolean isDictionary() { return false; }
		/** Returns the integer of node, or if the conversion is not possible, returns 0.
		 * @return The integer */
		default long getInteger() { return 0; }
		/** Returns the string of node, or if the conversion is not possible, returns null.
		 * @return The string */
		default String getString() { return null; }
		/** Returns the byte array of node, or if the conversion is not possible, returns null.
		 * @return The byte array */
		default byte[] getByteArray() { return null; }
		/** Returns the list of node, or if node is not list, an empty list.
		 * @return The list of node */
		default List<Node> getList() { return new Array(); }
		/** Returns the value of field of dictionary of node.
		 * @param field Field name
		 * @return Value of field, or if node is not dictionary, or field is not exist, return an empty node */
		default Node getField(String field) { return ABSENT_NODE; }
		/** Returns the names of fields of dictionary of node.
		 * @return The names of fields of dictionary of node, or if node is not dictionary, an empty list. */
		default String[] getFieldNames() { return new String[0]; }
	}

	/** Node for data that not exist. */
	static private final AbsentNode ABSENT_NODE = new AbsentNode();

	/** Node for data that not exist. */
	static private class AbsentNode implements Node {
		@Override public boolean isExist() { return false; }
	}

	/** Node for integer data. */
	static private class IntegerNode implements Node {
		IntegerNode(long l) { val = l; }
		/** Ο ακέραιος αριθμός. */
		long val;
		@Override public boolean isInteger() { return true; }
		@Override public long getInteger() { return val; }
	}

	/** Node for byte array data. */
	static private class ByteArrayNode implements Node {
		ByteArrayNode(byte[] s) { val = s; }
		/** The byte array. */
		byte[] val;
		@Override public boolean isByteArray() { return true; }
		@Override public String getString() { return new String(val, UTF_8); }
		@Override public byte[] getByteArray() { return val; }
	}

	/** Node for list data. */
	static private class ListNode implements Node {
		ListNode(Array a) { val = a; }
		/** The list. */
		Array val;
		@Override public boolean isList() { return true; }
		@Override public ArrayList<Node> getList() { return val; }
	}

	/** Node for dictionary data. */
	static private class DictionaryNode implements Node {
		DictionaryNode(TreeMap<String, Node> a) { val = a; }
		/** The dictionary. */
		TreeMap<String, Node> val;
		@Override public boolean isDictionary() { return true; }
		@Override public Node getField(String field) { return val.getOrDefault(field, ABSENT_NODE); }
		@Override public String[] getFieldNames() { return val.keySet().toArray(new String[0]); }
	}

	/** Convert a bencode encoding to an intermediate hierarchical object from which data can retreived.
	 * @param is Bencode input stream
	 * @return An hierarchical data structure from which data can retrieved
	 * @throws FormatException If text is not valid bencode
	 * @throws IOException On input error (e.g. damaged disk) */
	static public Node unserialize(InputStream is) throws FormatException, IOException {
		int b = is.read();
		switch(b) {
			case 'e': return null; // terminates lists and dictionaries
			case 'i': {	// integer
				String s = parseUntil(is, 'e');
				try { return new IntegerNode(Long.parseLong(s)); }
				catch(NumberFormatException ex) { throw new FormatException("Bad integer format: " + s, ex); }
			}
			case 'l': {	// list
				Array a = new Array();
				for(Node value = unserialize(is); value != null; value = unserialize(is))
					a.add(value);
				return new ListNode(a);
			}
			case 'd': {	// dictionary
				TreeMap<String, Node> map = new TreeMap<>();
				for(;;) {
					Node key = unserialize(is);
					if (key == null) break;
					if (!key.isByteArray()) throw new FormatException("Parsing dictionary: key must be string");
					Node value = unserialize(is);
					if (value == null) throw new FormatException("Parsing dictionary: value must exist");
					map.put(key.getString(), value);
				}
				return new DictionaryNode(map);
			}
			default: {	// string: starting with number
				int size;
				String s = (char) b + parseUntil(is, ':');
				try {
					size = Integer.parseInt(s);
					if (size < 0) throw new FormatException("Negative size format: " + s);
				} catch(NumberFormatException ex) { throw new FormatException("Bad size format: " + s, ex); }
				byte[] ar = new byte[size];
				if (size != is.read(ar)) throw new FormatException("Parsing string: " + size + " bytes expected");
				return new ByteArrayNode(ar);
			}
		}
	}

	/** Read text, until termination character.
	 * @param is Bencode input stream
	 * @param finishChar Termination character
	 * @return Text until termination character, without it
	 * @throws FormatException If text is not valid bencode
	 * @throws IOException On input error (e.g. damaged disk) */
	static private String parseUntil(InputStream is, int finishChar) throws IOException, FormatException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream(50);
		for (int c = is.read(); c != finishChar; c = is.read())
			if (c != -1) bos.write(c);
			else throw new FormatException("Parsing number: '" + finishChar + "' expected");
		return bos.toString();
	}

	/** Exception, when text is not valid bencode. */
	static public class FormatException extends Exception {
		/** Exception initialization.
		 * @param reason Friendly text with exception reason */
		FormatException(String reason) { super(reason); }
		/** Exception initialization.
		 * @param reason Friendly text with exception reason
		 * @param throwable A previous exception, which lead to this */
		FormatException(String reason, Throwable throwable) { super(reason, throwable); }
	}
}
