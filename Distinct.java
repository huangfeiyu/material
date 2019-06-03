
import java.io.File;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

/**
 *
 * @author huang
 */
public class Distinct {
    public static void main(String[] args) {
        boolean test = false;
        if(args.length == 0) {
            System.out.println("NAME\n"
                    + "\tDelete the duplicated files\n"
                    + "\n"
                    + "SYNOPSIS\n"
                    + "\tjava Distinct test\n"
                    + "\tjava Distinct exec\n"
                    + "\n"
                    + "DESCRIPTION\n"
                    + "\tUse java Distinct to delete the duplicated files from current directory and sub-directory, "
                    + "the command use the file name to identify the file, say if there are two files one is at aa/bb/cc/a.c "
                    + "another is at aa/a.c, the aa/a.c will be deleted. If two file has same file name, the one which has "
                    + "deeper directory tree will be kept, if the two file has the same deep directory tree, then the "
                    + "deletion can be any of the two.\n"
                    + "\n"
                    + "OPTIONS\n"
                    + "\ttest\n"
                    + "\t    list the files which going to be deleted after the execute the command, but doesn't really delete the file.\n"
                    + "\texec\n"
                    + "\t    execute the real deletion.");
            return;
        }
        if(args.length > 0) {
            test = "test".equals(args[0]);
        }
        System.out.println(Arrays.toString(args));
        File root = new File(".");
        List<File> files = findFile(root);
        Collections.sort(files, (f1, f2) -> {
            Path p1 = Paths.get(f1.getAbsolutePath());
            Path p2 = Paths.get(f2.getAbsolutePath());
            return p2.getNameCount() - p1.getNameCount();
        });
        Set<String> fileNames = new HashSet<>();
        for(File f : files) {
            if(fileNames.contains(f.getName())) {
                System.out.println("Delete File: " + f.getAbsolutePath());
                if(!test) {
                    f.delete();
                }
            } else {
                fileNames.add(f.getName());
            }
        }
    }
    
    private static List<File> findFile(File file) {
        List<File> files = new ArrayList<>();
        if(file.isFile()) {
            files.add(file);
            return files;
        }
        if(file.isDirectory()) {
            if(".git".equals(file.getName())) {
                return files;
            }
            for(File f : file.listFiles()) {
                files.addAll(findFile(f));
            }
        }
        return files;
    }
}
