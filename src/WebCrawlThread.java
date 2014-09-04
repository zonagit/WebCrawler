import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Thread crawls from a start url
 * @author zona
 *
 */
public class WebCrawlThread extends Thread
{
	//absolute maximum number of urls to crawl
	final int MAX_NUM_LINKS_TO_CRAWL = 1000;
	final int MAX_DEPTH = 1000;
	private ContentCrawlType crawlMode;
	private String startUrl;
	private int threadID;
	private SynchronizedManager synchronizedManager;
		
	//cache with disallowed urls as per the robots.txt file. The key is the host url
	//i.e. www.cnn.com, and the values are all urls disallowed for www.cnn.com
	private static Map<String, List<String>> disallowListCache = new  HashMap<>();
	
	// Crawl lists
	private static HashSet<String> crawledUrls = new HashSet<String>();
	private static LinkedHashSet<String> urlsToCrawl = new LinkedHashSet<String>();
	
	public WebCrawlThread(ContentCrawlType aCrawlMode,String aStartUrl, int aThreadID)
	{
		this.crawlMode = aCrawlMode;
		this.startUrl = aStartUrl;
		this.threadID = aThreadID;
		this.synchronizedManager = new SynchronizedManager();
	}
	
	public void init()
	{		
		this.start();
	}
	
	
	// Check if robot is allowed to access the given URL. 
	//Different threads can be accessing the map so make it synchronized to prevent race conditions
	//But it makes it a bottleneck
	/**
	 * 
	 * @param urlToCheck
	 * @return
	 */
	private synchronized boolean isRobotAllowed(URL urlToCheck) 
	{
		String host = urlToCheck.getHost().toLowerCase();
		
		// Retrieve host's disallow list from cache.
		List<String> disallowList = disallowListCache.get(host);
	       
		// If list is not in the cache, download and cache it.
		if (disallowList == null) 
		{
			disallowList = new ArrayList<String>();
			try 
			{
				URL robotsFileUrl =	new URL("http://" + host + "/robots.txt");
	            
	            // Open connection to robot file URL for reading.
	            BufferedReader reader =  new BufferedReader(new InputStreamReader(robotsFileUrl.openStream()));
	            
	            // Read robot file, creating list of disallowed paths.
	            String line;
	            while ((line = reader.readLine()) != null) 
	            {
	            	if (line.toLowerCase().indexOf("Disallow:") == 0) 
	            	{
	            		String disallowPath =  line.toLowerCase().substring("Disallow:".length());
	            		// Check disallow path for comments and remove if present.
	            		int commentIndex = disallowPath.indexOf("#");
	            		if (commentIndex != - 1) 
	            		{
	            			disallowPath = disallowPath.substring(0, commentIndex);
	            		}
	            		// Remove leading or trailing spaces from disallow path.
	            		disallowPath = disallowPath.trim();
	            		// Add disallow path to list.
	            		disallowList.add(disallowPath);
	            	}
	            }
	            // Add new disallow list to cache.
	            disallowListCache.put(host, disallowList);	     
			}
			catch (Exception e) 
			{
				 //Assume robot is allowed since an exception
	             //is thrown if the robot file doesn't exist. 
	             return true;
			}
		}
		
		//Loop through disallow list to see if
	    //crawling is allowed for the given URL. 
		String file = urlToCheck.getFile();
		for (int i = 0; i < disallowList.size(); i++) 
		{
			String disallow = (String) disallowList.get(i);
			if (file.startsWith(disallow)) 
			{
	             return false;
			}
		}
		return true;
	}
	  
	/**
	 * Fetches html for given url
	 * @param pageUrl
	 * @return
	 */
	private String fetchPageContent(URL pageUrl) 
	{		
		try 
		{
			// Open a connection to the URL and send a HEAD request
			HttpURLConnection.setFollowRedirects(false);
			HttpURLConnection con = (HttpURLConnection)pageUrl.openConnection();
			con.setAllowUserInteraction(true);
			con.setRequestMethod("HEAD");
			con.setDoOutput(true);
			con.setConnectTimeout(5000);
			con.setReadTimeout(5000);
			con.connect();
			
			// Check if the page exists and if it is an HTML file
			int code = con.getResponseCode();
			String type = con.getContentType();
			
			con.disconnect();
			
			if (code != HttpURLConnection.HTTP_OK || !type.contains("text/html")) 
			{
				return null;
			}
			
	 		// Open a connection to download the page content
			InputStream pageStream = pageUrl.openStream();
	 		BufferedReader reader = new BufferedReader(new InputStreamReader(pageStream));
			
	 		// Read the page line by line and write into the buffer
			String line;
			StringBuffer pageBuffer = new StringBuffer();
			
			while ((line = reader.readLine()) != null) 
			{
				pageBuffer.append(line);
			}
			pageStream.close();
			reader.close();
			
			// Return page content as a string
			return pageBuffer.toString();
			
		} 
		
		catch (Exception e) 
		{
			return null;
		}
	}
	
	
	private List<String> extractLinks(URL pageUrl, String pageContent, HashSet<String> crawledUrls) 
	{		
		// Create the regular expression for matching URLs
		//Starts with <a, followed by one or more white space chars, then href followed by 0 or more white
		//space chars, =
		Pattern pattern = Pattern.compile("<a\\s+href\\s*=\\s*\"?(.*?)[\"|>]", Pattern.CASE_INSENSITIVE);
		Matcher matcher = pattern.matcher(pageContent);
		
		// Create the list of extracted links
		List<String> linkList = new ArrayList<String>();
		int numLinksAdded =0;
		while (matcher.find()) 
		{			
			// Get the string inside the anchor href attribute
			String link = Utils.getCanonicalURL(matcher.group(1).trim());
			
			// Skip empty links
			if (link == null || link.isEmpty()) 
			{
				continue;
			}
			
			// Skip links that are just page anchors
			if (link.charAt(0) == '#') 
			{
				continue;
			}		
			
			// Skip mailto links
			if (link.toLowerCase().contains("mailto:")) 
			{
				continue;
			}
			
			// Skip JavaScript links
			if (link.toLowerCase().contains("javascript:")) 
			{
				continue;
			}
			
			
			// Construct absolute from relative URLs if necessary
			if (!link.contains("://")) 
			{			
				if (link.charAt(0) == '/') 
				{
					link = pageUrl.getProtocol() + "://" + pageUrl.getHost() + link;
				}
				else if (link.startsWith("../")) 
				{
					try 
					{
						URL absolute = new URL(pageUrl, link); 
						link = absolute.toString();
					} 
					catch (MalformedURLException e) 
					{
						link = "not valid";
					}
				}
				else 
				{
					String fileName = pageUrl.getFile();
					String linkBase = pageUrl.getProtocol() + "://" + pageUrl.getHost();
					
					if (!fileName.contains("/")) 
					{
						link = linkBase + "/" + link;
					} 
					else 
					{
						String path = fileName.substring(0, fileName.lastIndexOf('/') + 1);
						link = linkBase + path + link;
					}
				}
			}
			
			// If the link contains a named anchor, remove it
			int index = link.indexOf('#');
			if (index != -1) 
			{
				  link = link.substring(0, index);
			}
			
			//skip if it is the same as page url
			if (Utils.removeTrailingSlash(link).toLowerCase().equals(pageUrl.toString().toLowerCase()))
			{
				continue;
			}
			
			// Verify the link and skip if invalid
			URL checkedLink = Utils.checkUrl(link);
			if (checkedLink == null) 
			{
				continue;
			}
			//skip links outside domain if crawling in focused domain mode
			if (crawlMode == ContentCrawlType.FOCUSED_CRAWL_DOMAIN && !link.toLowerCase().contains(startUrl))
			{
				//System.out.println("(Thread " + threadID + ") Not crawling " + link + " (out of domain " + startUrl + ")");
				continue;
			}
			// Skip the link if it has already been crawled
			if (synchronizedManager.crawledUrlsSetContainsLink(link, crawledUrls)) 
			{
				continue;
			}
			
			// Add the link to the link list try to limit depth 
			if (numLinksAdded< MAX_DEPTH)
			{
				linkList.add(link);
				numLinksAdded++;
			}
			else
			{
				break;
			}
		}
		// Return the list of links found on the page
		return linkList;
	}
	
	
	public void crawl() 
	{		
		System.out.println("\n Thread " + threadID + " is starting crawling...\n");
		
		long startTime = System.currentTimeMillis();
		
		// Add the start URL to the list of URLs to crawl
		synchronizedManager.addToUrlsToCrawlSet(startUrl, urlsToCrawl);
		
		// Search until the number of found URLs reaches MAX_NUM_LINKS_TO_CRAWL or there are no more urls to crawl
		while (synchronizedManager.keepCrawling(urlsToCrawl, crawledUrls, MAX_NUM_LINKS_TO_CRAWL) )
		{
			// Get the URL
			String url = synchronizedManager.getFromUrlsToCrawlSet(urlsToCrawl, crawledUrls);
			
			// Check and convert the URL string to the URL object
			URL checkedUrl = Utils.checkUrl(url);
			
			// Skip URL if robots are not allowed to access it.
			if (checkedUrl != null && isRobotAllowed(checkedUrl)) 
			{
				// Download the page at the URL
				String pageContent = fetchPageContent(checkedUrl);
			 	if (pageContent != null && !pageContent.isEmpty()) 
			   	{					
			   		// Extract valid links from the page
					List<String> links = extractLinks(checkedUrl, pageContent, crawledUrls);
					
					// Add the links to the list of URLs to crawl
					if(!links.isEmpty()) 
					{
						synchronizedManager.addAllToUrlsToCrawlSet(links, urlsToCrawl);
					}
					
//				   	// Add the page to the list of crawled URLs
//				 	crawledUrls.add(url);
				   	
				 	// Display the crawled URL
				   	System.out.println("(Thread " + threadID + ") " + url);
			   	}
			}			
		}
		if(synchronizedManager.crawledUrlsSetSize(crawledUrls)>0) 
		{			
			long endTime = System.currentTimeMillis();
			DateFormat formatter = new SimpleDateFormat("mm:ss");
			String totalTime = formatter.format(endTime - startTime);
			
			System.out.println("\n (Thread " +  threadID + ") Done. " + synchronizedManager.crawledUrlsSetSize(crawledUrls) + " URLs found. Total time: " + totalTime);
		}
		else
			System.out.println("No valid URL could be found.");
	}
	
	@Override
    public void run()
    {
		crawl();
    }
}
