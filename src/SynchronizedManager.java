import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;

public class SynchronizedManager 
{
	synchronized void addToUrlsToCrawlSet(String url, LinkedHashSet<String> urlsToCrawl)
	{
		urlsToCrawl.add(url);
	}
	
	synchronized void addAllToUrlsToCrawlSet(List<String> links, LinkedHashSet<String> urlsToCrawl)
	{
		urlsToCrawl.addAll(links);
	}	
	
	synchronized String getFromUrlsToCrawlSet(LinkedHashSet<String> urlsToCrawl, HashSet<String> crawledUrls)
	{
		String url =  urlsToCrawl.iterator().next();
		//remove the page from the list of urls to crawl
		urlsToCrawl.remove(url);
		
		// Add the page to the list of crawled URLs so that this page is not crawled again by a different thread
		crawledUrls.add(url);
		
		return url;
	}
	
	synchronized boolean keepCrawling(LinkedHashSet<String> urlsToCrawl, HashSet<String> crawledUrls, int maxLinksToCrawl)
	{
		return !urlsToCrawl.isEmpty() && crawledUrls.size() <= maxLinksToCrawl;
	}
	
	synchronized boolean crawledUrlsSetContainsLink(String link, HashSet<String> crawledUrls)
	{
		return crawledUrls.contains(link);
	}
	
	synchronized int crawledUrlsSetSize(HashSet<String> crawledUrls)
	{
		return crawledUrls.size();
	}
}
