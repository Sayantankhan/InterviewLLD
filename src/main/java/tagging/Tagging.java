package tagging;

import java.util.*;

public class Tagging {
    /*
        Requirements
         Tagging related content across these products
            - JIRA
            - Confluence
            - BitBucket

        Functional Requirement
          - Add/Remove tag to a content
          = Search By Tag
          - Retrives Tag from a content
          - Search by Tag
          - View popular Tags

        Tag  => tagid, tagname, description, ts
        Content => id, title, Set<Tag>
        content_tag => id, tagid, contentid, productType(Jira, confluence, bitbucket)
        TagManager  => Map<Tag, List<Content>>
     */
    static class TagPair {
        String tagId;
        int count;

        TagPair(String tagId, int count) {
            this.tagId = tagId;
            this.count = count;
        }
    }
    static class Tag {
        String id;
        String name;
        String description;
        long ts;

        Tag(String id, String name, String description) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.ts = System.currentTimeMillis();
        }
    }

    enum CONTENT_TYPE {
        JIRA,
        CONFLUENCE;
    }

    static abstract class Content {
        String id;
        String title;
        Set<Tag> tags;
        CONTENT_TYPE type;

        long ts;

        public Content(String id, String title) {
            this.id = id;
            this.title = title;
            this.ts = System.currentTimeMillis();
        }
    }

    static class JiraTicket extends Content{

        public JiraTicket(String id, String title, Set<Tag> tags) {
            super(id, title);
            this.tags = tags;
            this.type = CONTENT_TYPE.JIRA;
        }
    }

    static class Confluence extends Content {

        public Confluence(String id, String title, Set<Tag> tags){
            super(id, title);
            this.tags = tags;
            this.type = CONTENT_TYPE.CONFLUENCE;
        }
    }

    static class ContentManager {
        final Map<String, Content> contentMap;
        final TagManager tagManager;
        static ContentManager contentManager;

        private ContentManager(){
            contentMap = new HashMap<>();
            tagManager = TagManager.getInstance();
        }

        static synchronized ContentManager getInstance() {
            if(contentManager == null) {
                contentManager = new ContentManager();
            }
            return contentManager;
        }

        public void addContent(String contentId, String title, CONTENT_TYPE type, Set<Tag> tags) {
            Content content;
            switch (type){
                case JIRA -> content = new JiraTicket(contentId, title, tags);
                case CONFLUENCE -> content = new Confluence(contentId, title, tags);
                default -> throw new IllegalArgumentException();
            }
            contentMap.put(contentId, content);
            tagManager.addTags(content);
        }

        public void removeContent(String contentId) {
            Content content = contentMap.get(contentId);
            tagManager.removeTags(content);
        }
    }


    static class TagManager {
        final Map<String, List<Content>> tag_content_map;
        final Map<String, Tag> tag_map;
        private static TagManager instance;
        private TagManager(){
            tag_content_map = new HashMap<>();
            tag_map = new HashMap<>();
        }

        static synchronized TagManager getInstance() {
            if(instance == null) {
                instance = new TagManager();
            }
            return instance;
        }

        public synchronized void addTags(Content content) {
            for(Tag tag : content.tags) {
                tag_content_map.computeIfAbsent(tag.id, k -> new ArrayList<>()).add(content);
                tag_map.computeIfAbsent(tag.id, k -> tag);
            }
        }

        public synchronized void removeTags(Content content) {
            for(Tag tag : content.tags) {
                List<Content> list = tag_content_map.get(tag);
                if(list == null) continue;

                if( list.size() == 1){
                    tag_content_map.remove(tag);
                    tag_map.remove(tag.id);
                } else {
                    tag_content_map.get(tag).remove(content);
                }
            }
        }

        // Search By Tag
        public List<Content> searchByTag(String tag) {
            return tag_content_map.get(tag);
        }

        // view popular tag
        public List<Tag> viewPopularTags(int k) {
            List<Tag> tags = new ArrayList<>();
            PriorityQueue<TagPair> minHeap = new PriorityQueue<>(k, Comparator.comparing(t1 -> t1.count));
            Iterator<Map.Entry<String, List<Content>>> itr = tag_content_map.entrySet().iterator();
            int count = 0;

            while(itr.hasNext()) {
                Map.Entry<String, List<Content>> entry = itr.next();

                if(count < k) {
                    minHeap.offer(new TagPair(entry.getKey(), entry.getValue().size()));
                    count++;
                } else {
                    TagPair tp = minHeap.peek();
                    if(tp.count < entry.getValue().size()) {
                        minHeap.poll();
                        minHeap.offer(new TagPair(entry.getKey(), entry.getValue().size()));
                    }
                }
            }

            while (!minHeap.isEmpty()) {
                tags.add(tag_map.get(minHeap.poll().tagId));
            }


            return tags;
        }
    }

    public static void main(String[] args) {
        ContentManager cm = ContentManager.getInstance();
        TagManager tm = TagManager.getInstance();


        Tag sre = new Tag("#sre", "sre", "");
        Tag ha = new Tag("#ha", "ha", "");
        Tag load = new Tag("#load", "load", "");
        Tag dsa = new Tag("#dsa", "dsa", "");
        Tag interview = new Tag("#interview", "interview", "");
        Tag p1 = new Tag("#p1", "p1", "");
        Tag p2 = new Tag("#p2", "p2", "");

        cm.addContent("jira-1", "jira-1", CONTENT_TYPE.JIRA, new HashSet<Tag>(Arrays.asList(sre, load, p1)));
        cm.addContent("jira-2", "jira-2", CONTENT_TYPE.JIRA,new HashSet<Tag>(Arrays.asList(ha, p2)));
        cm.addContent("jira-3", "jira-3", CONTENT_TYPE.JIRA,  new HashSet<Tag>(Arrays.asList(sre, p2)));

        cm.addContent("c-1", "c-1", CONTENT_TYPE.CONFLUENCE, new HashSet<Tag>(Arrays.asList(dsa, interview, ha)));
        cm.addContent("c-2", "c-2", CONTENT_TYPE.CONFLUENCE, new HashSet<Tag>(Arrays.asList(dsa, load, ha)));
        cm.addContent("c-3", "c-3", CONTENT_TYPE.CONFLUENCE, new HashSet<Tag>(Arrays.asList(sre, interview, p1)));
        cm.addContent("c-4", "c-4", CONTENT_TYPE.CONFLUENCE, new HashSet<Tag>(Arrays.asList(interview, load, ha)));

        tm.searchByTag("#load").forEach(t -> System.out.println(t.title + " " + t.type));
        System.out.println("---------- popular tags --------------------");
        tm.viewPopularTags(3).forEach(t -> System.out.println(t.id));

    }
}
