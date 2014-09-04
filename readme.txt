To compile from the command line and assuming that your path variable is pointing to the jdk bin folder, and your JAVA_HOME is
also set, run from the src folder

javac -cp ".;../external/lib/jsoup-1.7.3.jar" *.java

Then run from the same folder with

java WebCrawlerMain

Even better import the project into eclipse and run it from there.

The crawling algorithm is breadth first search when single threaded

If user enters more than one base url to crawl, a thread will be spawned for each. Threads can access any of the links
found by any other thread.

Adjustable params:

In WebCrawlerMain.java

private static final int NUM_SEARCH_RESULTS = 3;

controls how many google search results to use to seed the topical crawling
(an equal number of threads will be spawned)

In WebCrawlThread.java

final int MAX_NUM_LINKS_TO_CRAWL = 1000;

controls the maximum number of urls to crawl 

and

final int MAX_DEPTH = 1000;

how many links from each page to retrieve

The data structures 

private static HashSet<String> crawledUrls = new HashSet<String>();
private static LinkedHashSet<String> urlsToCrawl = new LinkedHashSet<String>();

in WebCrawlThread.java are shared by all threads and access to them is done via synchronized methods to prevent race conditions
among the threads. In particular the method getFromUrlsToCrawlSet will retrieve the next url from the LinkedHashSet urlsToCrawl
(this set keeps insertion order), remove it and added it to the set crawledUrls.

Since all of this method is executed by a single thread this guarantees that no two threads crawl the same url.






	

