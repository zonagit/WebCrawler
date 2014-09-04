import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;


public class WebCrawlerMain 
{
	private static ContentCrawlType crawlMode;
	private static final int NUM_SEARCH_RESULTS = 5;//how many google search results to use in topical crawling
	
	public static void main(String[] args) throws IOException 
	{    	
		InputStreamReader converter = new InputStreamReader(System.in);
		BufferedReader in = new BufferedReader(converter);
		System.out.println("Enter the type of crawling: 1 (General Crawling), 2 (Focused domain Crawling) or 3 (Focused topic Crawling).");
		crawlMode = ContentCrawlType.GENERAL_CRAWL;
		while (true)
		{
			String mode = in.readLine();
			if (!"1".equals(mode.trim()) && !"2".equals(mode.trim()) && !"3".equals(mode.trim()))
				System.out.println("Invalid mode. Enter 1 (General), 2( Focused Domain) or 3 (Focused Topic)");
			else
			{
				if ("2".equals(mode.trim()))
					crawlMode = ContentCrawlType.FOCUSED_CRAWL_DOMAIN;
				else if ("3".equals(mode.trim()))
					crawlMode = ContentCrawlType.FOCUSED_CRAWL_TOPIC;
				break;
			}
		}
		
		List<WebCrawlThread> crawlThreads = new ArrayList<>();
		if (crawlMode == ContentCrawlType.GENERAL_CRAWL || crawlMode == ContentCrawlType.FOCUSED_CRAWL_DOMAIN)
		{
			String startUrl = null;
			System.out.println("Enter the URL where to start crawling from:");
			int startUrlNum = 0;
			while (true) 
			{
				startUrl = in.readLine();
				if (!"q".equals(startUrl.trim()) && Utils.checkUrl(startUrl) != null)
				{
					startUrlNum++;
					// create a thread for crawling
					crawlThreads.add(new WebCrawlThread(crawlMode, startUrl, startUrlNum));
					System.out.println("Another URL? (q to quit)");
				}
				else if ("q".equals(startUrl.trim()))
				{
					break;
				}
				else
					System.out.println("The given URL is not valid. Please enter a valid URL or q to quit.");
			}
			
			// Start crawling
			for (WebCrawlThread crawlThread : crawlThreads)
			{
				crawlThread.init();
			}
		}		
		else if (crawlMode == ContentCrawlType.FOCUSED_CRAWL_TOPIC)//focused topic crawler
		{
			String search;
			System.out.println("Enter your topic");
			
			search = in.readLine();
			//do google search on topic			
			String googleQuery = "http://www.google.com/search?q=";
			String charset = "UTF-8";
			//Chrome
			String userAgent = "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/36.0.1985.67 Safari/537.36";
			Elements links = Jsoup.connect(googleQuery +  URLEncoder.encode(search, charset)).userAgent(userAgent).get().select("a[href]");
			System.out.println("Crawling through top " + NUM_SEARCH_RESULTS + " Google search results for topic " + search);
			int intCountResults = 0;
			//crawl the first NUM_SEARCH_RESULTS links
			for (Element link : links) 
			{
			    String title = link.text();
			    String startUrl = link.absUrl("href"); // Google returns URLs in format "http://www.google.com/url?q=<url>&sa=U&ei=<someKey>".
			    
			    //url = URLDecoder.decode(url.substring(url.indexOf('=') + 1, url.indexOf('&')), "UTF-8");

			    if (!startUrl.startsWith("http") || startUrl.contains("google.com")) 
			    {
			        continue; // Ads/news/etc.
			    }
			   
			    System.out.println("Title: " + title);
			    System.out.println("URL: " + startUrl);
			    
			    // Start crawling
			    if (Utils.checkUrl(startUrl) != null && intCountResults < NUM_SEARCH_RESULTS)
			    {
			    	 intCountResults++;
			    	// Start a thread for crawling
					WebCrawlThread crawlThread = new WebCrawlThread(crawlMode, startUrl,intCountResults );
					crawlThread.init();
			    }
			    else
			    {
			    	break;
			    }
			}
		}
	}
}
