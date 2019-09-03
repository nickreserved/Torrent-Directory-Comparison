package torrent;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import static java.lang.System.out;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import torrent.BencodeSerializer.FormatException;
import torrent.BencodeSerializer.Node;

/** A program which:
<ul><li>Displays all file paths inside a torrent.
<li>Scans a directory for files and displays differences with torrent files.</ul>*/
public class Torrent {

	/** Main program
	 * @param args Program command line parameters */
	public static void main(String[] args) {
		Params params = checkParams(args);
		if (params == null) { help(); return; }
		Node torrent = loadTorrent(args[0]);
		ArrayList<String> torrentfiles = getTorrentFiles(torrent);
		if (params.dir == null) torrentfiles.forEach(i -> out.println(i));
		else printComparison(params, torrentfiles);
	}

	/** Displays a comparison between torrent file paths and directory file paths.
	 * Scan gived directory for files and compare with file paths inside torrent. Then displays
	 * differences.
	 * @param params Parameters given from command line
	 * @param torrentfiles A list of all file paths inside torrent */
	static private void printComparison(Params params, ArrayList<String> torrentfiles) {
		ArrayList<String> dirfiles = getDirFiles(params.dir);
		ArrayList<String> equalfiles = new ArrayList<>(dirfiles.size());
		ArrayList<String> addfiles = new ArrayList<>(torrentfiles.size());
		ArrayList<String> delfiles = new ArrayList<>(dirfiles.size());
		Collections.sort(dirfiles); Collections.sort(torrentfiles);
		int dirIdx = 0, torIdx = 0;
		while(dirIdx < dirfiles.size() && torIdx < torrentfiles.size()) {
			String tor = torrentfiles.get(torIdx);
			String dir = dirfiles.get(dirIdx);
			int cmp = tor.compareTo(dir);
			if (cmp == 0) { equalfiles.add(tor); ++torIdx; ++dirIdx; }
			else if (cmp < 0) { addfiles.add(tor); ++torIdx; }
			else { delfiles.add(dir); ++dirIdx; }
		}
		if (params.equal) equalfiles.forEach(i -> out.println("=" + i));
		if (params.minus) delfiles.forEach(i -> out.println("-" + i));
		if (params.plus) addfiles.forEach(i -> out.println("+" + i));
	}

	/** Get all file paths of directory and its subdirectories.
	 * @param dir A directory
	 * @return A list with all file paths in directory tree */
	static private ArrayList<String> getDirFiles(String dir) {
		ArrayList<String> files = new ArrayList<>(10000);
		addDirFiles(new File(dir), files);
		int start = dir.length() + 1;
		for (int z = 0; z < files.size(); ++z)
			files.set(z, files.get(z).substring(start));
		return files;
	}

	/** All all files of given directory tree to a list.
	 * @param dir A directory
	 * @param files A list of files where files of directory dir and all of subdirectories will be added */
	static private void addDirFiles(File dir, ArrayList<String> files) {
		for (File f : dir.listFiles())
			if (f.isDirectory()) addDirFiles(f, files);
			else files.add(f.getPath());
	}

	/** Extract all file paths of torrent.
	 * @param torrent Torrent intermediate hierarchical data structure
	 * @return A list with all file paths in torrent */
	static private ArrayList<String> getTorrentFiles(Node torrent) {
		torrent = torrent.getField("info").getField("files");
		if (!torrent.isList()) return null;
		List<Node> list = torrent.getList();
		ArrayList<String> a = new ArrayList<>(list.size());
		for (Node record : list) {
			record = record.getField("path");
			list = record.getList();
			StringBuilder f = new StringBuilder(1000);
			f.append(list.get(0).getString());
			for (int z = 1; z < list.size(); ++z)
				f.append(File.separatorChar).append(list.get(z).getString());
			a.add(f.toString());
		}
		return a;
	}

	/** Data from command line program parameters. */
	static private class Params {
		/** Directory to check torrent contents. Can be null. */
		String dir;
		/** Output all files in torrent, do not exist in directory. */
		boolean plus = true;
		/** Output all files in directory, do not exist in torrent. */
		boolean minus = true;
		/** Output all files exist both in directory and torrent. */
		boolean equal = true;
	}

	/** Check for valid program parameters.
	 * @param args Program command line parameters
	 * @return A structure with data from command line paramaters, or null on error */
	static private Params checkParams(String[] args) {
		if (args.length < 1 || !args[0].endsWith(".torrent")) return null;
		Params p = new Params();
		if (args.length > 1) {
			p.dir = args[1];
			if (args.length > 2) {
				p.plus = p.minus = p.equal = false;
				int z = 2;
				if (args[z].equals("+")) {
					p.plus = true;
					if (args.length == ++z) return p;
				}
				if (args[z].equals("-")) {
					p.minus = true;
					if (args.length == ++z) return p;
				}
				if (args[z].equals("=")) { p.equal = true; return p; }
				else return null;
			}
		}
		return p;
	}

	/** Load torrent to an intermediate hierarchical data structure.
	 * If an error occur, program terminate with an error message.
	 * @param filename Filename of torrent file
	 * @return An intermediate hierarchical data structure */
	static private Node loadTorrent(String filename) {
		try { return BencodeSerializer.unserialize(new FileInputStream(filename)); }
		catch (FileNotFoundException ex) { error("Torrent file: " + filename + " not found"); }
		catch (FormatException ex) { error("Torrent file: " + filename + " has bad bencode format : " + ex.getMessage()); }
		catch (IOException ex) { error("IO error accessing torrent file: " + filename); }
		return null;
	}

	/** Display error message and terminate with error. */
	static private void error(String s) { out.println(s); System.exit(1); }

	/** Display help and terminate with error, because of invalid arguments. */
	static private void help() {
		error("Program parameters:\nfile.torrent [directory [+] [-] [=]]\n"
				+ "file.torrent\tThe torrent file\n"
				+ "directory\tDirectory to compare contents with torrent file\n"
				+ "+\t\tDisplay files in torrent file, not exist in directory\n"
				+ "-\t\tDisplay files in directory, not exist in torrent\n"
				+ "=\t\tDisplay files existed in both directory and torrent\n"
				+ "Absent of + - *, means all of them");
	}

}
