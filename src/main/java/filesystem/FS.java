package filesystem;

import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class FS {
    // Design an in-memory file system
    // Requirement
    // create a file
    // delete a file
    // read a file
    // create a dir
    // delete a dir
    // list a dir

    enum FSElements {
        FILE,
        DIR;
    }

    static class FSObj {
        String name;
        FSElements element;
        long ts;

        FSObj() {
            this.ts = System.currentTimeMillis();
        }
    }

    static class File extends FSObj {
        String content;
        Lock lock;

        File(String name) {
            super();
            this.name = name;
            this.lock = new ReentrantLock();
            this.element = FSElements.FILE;
        }

        File(String name, String content) {
            this(name);
            this.content = content;
        }

        void putContent(String s) {
            this.content = s;
        }

        public String read(){
            return this.content;
        }
    }

    static class Dir extends FSObj {

        final Map<String, FSObj> fileMap;

        Dir(String name) {
            super();
            this.name = name;
            this.fileMap = new HashMap<>();
            this.element = FSElements.DIR;
        }

        public List<String> listFile() {
            return fileMap.entrySet().stream().filter(e -> e.getValue().element == FSElements.FILE).map(e -> e.getKey()).collect(Collectors.toList());
        }

        public Set<String> list() {
            return fileMap.keySet();
        }

        public boolean createFSObj(String name, FSElements elements, String content) {
            FSObj obj;
            if(elements == FSElements.FILE) {
                obj = new File(name, content);
            } else {
                obj = new Dir(name);
            }
            fileMap.put(name, obj);
            return true;
        }
    }


    static class FileSystemManager {

        private static FileSystemManager fsManager;
        private Dir root;
        private FileSystemManager() {
            root = new Dir("root");
        }

        public static synchronized FileSystemManager getInstance() {
            if(fsManager == null) {
                fsManager = new FileSystemManager();
            }
            return fsManager;
        }

        private FSObj parsePath(String path) throws Exception {
            if(path.equals("/")) return root;
            Map<String, FSObj> map = root.fileMap;

            String[] tokens = path.split("/");
            Dir result = root;
            for(int i=1; i < tokens.length; i++) {
                String token = tokens[i];
                if(map.get(token) != null && map.get(token).element == FSElements.DIR) {
                    result = (Dir)map.get(token);
                    map = result.fileMap;
                } else {
                    throw new Exception("Path doesnot exists "+ path );
                }
            }

            return  result;
        }

        public boolean createFolder(String path, String name) throws Exception {
            Dir result = (Dir) parsePath(path);
            if(result == null) throw new IllegalArgumentException();
            result.createFSObj(name, FSElements.DIR, null);
            return true;
        }


        public boolean createFileRec(String path, String name, String content) throws Exception {
            if(path.equals("/")) return createFolder(path, name);
            Map<String, FSObj> map = root.fileMap;

            String[] tokens = path.split("/");
            Dir result = root;
            for(int i=1; i < tokens.length; i++) {
                String token = tokens[i];
                if (map.get(token) != null) {
                    if(map.get(token).element == FSElements.DIR) {
                        result = (Dir)map.get(token);
                        map = result.fileMap;
                    } else {
                        throw new Exception("Path doesnot exists "+ path );
                    }
                } else {
                    result.createFSObj(token, FSElements.DIR, null);
                    map = result.fileMap;
                    result = (Dir)result.fileMap.get(token);
                }
            }

            result.createFSObj(name, FSElements.FILE, content);
            return true;
        }

        public boolean createFile(String path, String name, String content) throws Exception {
            Dir result = (Dir) parsePath(path);
            if(result == null) throw new IllegalArgumentException();
            result.createFSObj(name, FSElements.FILE, content);
            return true;
        }

        public String read(String path, String file_name) throws Exception {
            FSObj result = parsePath(path);
            if(result.element == FSElements.DIR) {
                return ((File)((Dir)result).fileMap.get(file_name)).content;
            } else {
                throw new IllegalArgumentException("Cant read a folder");
            }
        }

        public Set<String> list(String path) throws Exception {
            FSObj result = parsePath(path);
            if(result.element == FSElements.DIR) {
                return ((Dir)result).list();
            }
            return null;
        }

        public List<String> list_file(String path) throws Exception {
            FSObj result = parsePath(path);
            if(result.element == FSElements.DIR) {
                return ((Dir)result).listFile();
            }
            return null;
        }

    }

    public static void main(String[] args) {
        FileSystemManager manager = FileSystemManager.getInstance();
        try {
            boolean is_created = manager.createFile("/", "docker.yaml", "docker-service: elastic-stack");
            if(is_created) {
                manager.list("/").forEach(e -> System.out.println(e));
                System.out.println(manager.read("/", "docker.yaml"));
            }

            System.out.println("========== Creating Folder ===============");
            boolean is_dir_create = manager.createFolder("/", "golang");
            if(is_dir_create) {
                manager.list("/").forEach(e -> System.out.println(e));
                manager.createFile("/golang", "mod.go", "go-version >= 1.23.4");
                manager.list("/golang").forEach(e -> System.out.println(e));
            }

            System.out.println("========== Creating Folder Rec ===============");
            is_created = manager.createFileRec("/a/b/c/d/e", "abcde.yaml", "hello worldd");
            if(is_created) {
                System.out.println(manager.read("/a/b/c/d/e", "abcde.yaml"));
            }

            manager.list("/").forEach(e -> System.out.println(e));
            manager.list("/a").forEach(e -> System.out.println(e));
            manager.list("/a/b").forEach(e -> System.out.println(e));
            manager.list("/a/b/c/d/e").forEach(e -> System.out.println(e));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
