/*
 * Decompiled with CFR 0_98.
 * 
 * Could not load the following classes:
 *  map.Item
 *  org.apache.commons.io.IOUtils
 *  org.apache.http.HttpEntity
 *  org.apache.http.HttpResponse
 *  org.apache.http.client.HttpClient
 *  org.apache.http.client.methods.HttpGet
 *  org.apache.http.client.methods.HttpUriRequest
 *  org.apache.http.impl.client.DefaultHttpClient
 *  org.apache.http.params.BasicHttpParams
 *  org.apache.http.params.HttpConnectionParams
 *  org.apache.http.params.HttpParams
 *  org.clapper.util.html.HTMLUtil
 */
package map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.clapper.util.html.HTMLUtil;

import java.io.*;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.net.UnknownHostException;
import java.text.NumberFormat;
import java.text.ParseException;
import java.util.*;

public class Cdw
{
    public static HttpClient client;
    public static int index;
    public static String cookie;
    public static ArrayList<Item> items;
    public static HashMap<String, ArrayList<Item>> itemMaps;

    static
    {
        index = 1;
        cookie = "";
        items = new ArrayList();
        itemMaps = new HashMap();
    }

    public static void filterItems()
    {
        Set<String> keys = itemMaps.keySet();
        for (String key : keys)
        {
            ArrayList<Item> values = itemMaps.get(key);
            if (values.size() == 1)
            {
                items.add(values.get(0));
                continue;
            }
            Double price = Cdw.formatAmount(values.get(0).getSalesPrice().substring(1));
            int lowestIndex = 0;
            for (int i = 0; i < values.size(); ++i)
            {
                Double currentPrice = Cdw.formatAmount(values.get(i).getSalesPrice().substring(1));
                if (currentPrice > price)
                {
                    continue;
                }
                price = currentPrice;
                lowestIndex = i;
            }
            int totalOfLowest = 0;
            for (int i2 = 0; i2 < values.size(); ++i2)
            {
                Double currentPrice = Cdw.formatAmount(values.get(i2).getSalesPrice().substring(1));
                if (currentPrice.doubleValue() != price.doubleValue())
                {
                    continue;
                }
                ++totalOfLowest;
            }
            Item item = values.get(lowestIndex);
            item.setQuantity(Integer.valueOf(totalOfLowest));
            item.setQuantity2(Integer.valueOf(values.size() - totalOfLowest));
            items.add(item);
        }
    }

    public static void addToItems(Item item)
    {
        Double price;
        if (item.getSalesPrice() != null && (price = Cdw.formatAmount(item.getSalesPrice().substring(1))) != null)
        {
            if (itemMaps.containsKey(item.getPartNumber()))
            {
                itemMaps.get(item.getPartNumber()).add(item);
            } else
            {
                ArrayList<Item> items = new ArrayList<Item>();
                item.setQuantity(Integer.valueOf(1));
                item.setQuantity2(Integer.valueOf(0));
                items.add(item);
                itemMaps.put(item.getPartNumber(), items);
            }
        }
    }

    public static Double formatAmount(String amount)
    {
        NumberFormat format = NumberFormat.getInstance(Locale.US);
        try
        {
            Number number = format.parse(amount);
            return number.doubleValue();
        }
        catch (ParseException e)
        {
            return null;
        }
    }

    public static void main(String[] args) throws Exception
    {
        BasicHttpParams params = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout((HttpParams) params, (int) 10000);
        HttpConnectionParams.setSoTimeout((HttpParams) params, (int) 10000);
        client = new DefaultHttpClient((HttpParams) params);
        String down = args[2].replaceAll("\"", "");
        String imgFolder = args[1].replaceAll("\"", "");
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter((OutputStream) new FileOutputStream(args[0].replaceAll("\"", "")), "UTF8"));
        out.write("Part Number,URL,Title,Manufacturer,CDW Number,Description,List Price,Sale Price,Image URL,Image Filename, Quantity1, Quantity2\r\n");
        out.flush();
        int counter = 0;
        ArrayList<String> list = new ArrayList<String>();
        for (int i = 1; i < 250; ++i)
        {
            String u;
            String h = Cdw.getHTML("http://www.cdw.com/shop/search/results.aspx?pCurrent=" + i + "&pPage=1&key=&x=7&y=9&wclsscat=&searchscope=All&sr=1&outlet=1");
            if (h.indexOf("hprProductLink") < 0)
            {
                break;
            }
            while (!(u = Cdw.extractDataWithHTML((String) h, "hprProductLink", "</")).equals(""))
            {
                Item item = new Item();
                item.setInStock(Boolean.valueOf(false));
                h = h.substring(h.indexOf("hprProductLink") + 5);
                u = Cdw.extractData(u, "href=\"", "\"");
                u = "http://www.cdw.com" + u;
                String h1 = Cdw.getHTML(u);
                item.setUrl(u);
                String cdw = Cdw.extractData(h1, "CDW Part:", "</");
                item.setCdw(cdw);
                int ind = Collections.binarySearch(list, cdw);
                if (ind < 0 || list.size() == 0)
                {
                    list.add(-1 * ind - 1, cdw);
                    String title = Cdw.extractData(h1, "<span class=\"fn\">", "</");
                    if (title.indexOf("(") >= 0 && title.indexOf(")") < 0)
                    {
                        title = title.substring(0, title.lastIndexOf("(")).trim();
                    }
                    item.setTitle(title);
                    String partNo = Cdw.extractData(h1, "Mfg. Part:", "</");
                    if (partNo.endsWith("-BSTK"))
                    {
                        partNo = partNo.substring(0, partNo.indexOf("-BSTK")).trim();
                    }
                    if (partNo.indexOf("#") >= 0)
                    {
                        partNo = partNo.substring(0, partNo.indexOf("#")).trim();
                    }
                    item.setPartNumber(partNo);
                    String img = Cdw.extractData(h1, "class=\"photo\" src=\"", "\"");
                    String filename = "";
                    if (!img.equals(""))
                    {
                        filename = String.valueOf(partNo) + ".jpg";
                        if (down.equals("1"))
                        {
                            Cdw.downloadPhoto(img, imgFolder, filename);
                        }
                        item.setImageFileName(filename);
                        item.setImageUrl(img);
                    } else
                    {
                        item.setImageFileName("");
                        item.setImageUrl("");
                    }
                    String availability = Cdw.extractData(h1, "Availability:</span>", "</");
                    if (availability.equals("In Stock"))
                    {
                        item.setInStock(Boolean.valueOf(true));
                        String desc = Cdw.extractDataWithHTML(h1, "<div class=\"feature-list\">", "</div>");
                        desc = desc.replaceAll("</li>", ", ");
                        desc = desc.replaceAll("\\<.*?\\>", "").replaceAll("\\s+", " ").trim();
                        if (desc.endsWith(","))
                        {
                            desc = desc.substring(0, desc.length() - 1).trim();
                        }
                        item.setDescription(desc);
                        String listPrice = Cdw.extractData(h1, "stockOriginalPrice\">", "</");
                        item.setListPrice(listPrice);
                        String salePrice = Cdw.extractData(h1, "selected-price price\">", "</");
                        item.setSalesPrice(salePrice);
                        String mfg = Cdw.extractData(h1, "manufacturerLogo\" title=\"", "\"");
                        item.setManufacturer(mfg);
                        System.out.println(String.valueOf(i) + " # of items collected: " + ++counter);
                    }
                }
                Cdw.addToItems(item);
            }
        }
        Cdw.filterItems();
        for (Item item : items)
        {
            out.write("\"" + item.getPartNumber().replaceAll("\"", "\"\"") + "\",\"" + item.getUrl().replaceAll("\"", "\"\"") + "\",\"" + item.getTitle().replaceAll("\"", "\"\"") + "\",\"" + item.getManufacturer().replaceAll("\"", "\"\"") + "\",\"" + item.getCdw().replaceAll("\"", "\"\"") + "\",\"" + item.getDescription().replaceAll("\"", "\"\"") + "\",\"" + item.getListPrice().replaceAll("\"", "\"\"") + "\",\"" + item.getSalesPrice().replaceAll("\"", "\"\"") + "\",\"" + item.getImageUrl().replaceAll("\"", "\"\"") + "\",\"" + item.getImageFileName().replaceAll("\"", "\"\"") + "\",\"" + item.getQuantity().toString() + "\",\"" + item.getQuantity2().toString() + "\"\r\n");
        }
        out.flush();
        out.close();
    }

    public static String downloadPhoto(String address, String path, String fileName)
    {
        String html;
        block13:
        {
            try
            {
                URL url2 = new URL(address);
                URI uri = new URI(url2.getProtocol(), url2.getUserInfo(), url2.getHost(), url2.getPort(), url2.getPath(), url2.getQuery(), url2.getRef());
                address = uri.toASCIIString();
            }
            catch (Exception url2)
            {
                // empty catch block
            }
            html = "";
            if (!path.endsWith("\\"))
            {
                path = String.valueOf(path) + "\\";
            }
            HttpGet method = null;
            int j = 0;
            try
            {
                html = "";
                method = new HttpGet(address);
                HttpResponse response = client.execute((HttpUriRequest) method);
                byte[] responseBody = IOUtils.toByteArray((InputStream) response.getEntity().getContent());
                FileOutputStream out = new FileOutputStream(String.valueOf(path) + fileName);
                out.write(responseBody);
                out.close();
            }
            catch (Exception ex)
            {
                ex.printStackTrace();
                System.out.println(address);
                if (ex.getClass().toString().indexOf("UnknownHostException") >= 0)
                {
                    try
                    {
                        --j;
                        Thread.sleep(10000);
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    break block13;
                }
                if (ex.getClass().toString().indexOf("FileNotFoundException") >= 0)
                {
                    j = 111;
                    break block13;
                }
                try
                {
                    Thread.sleep(10000);
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return html;
    }

    public static String extractDataUrl(String html, String s2, String s3)
    {
        int i = html.indexOf(s2);
        String result = "";
        if (i >= 0)
        {
            int j = (html = html.substring(i + s2.length())).indexOf(s3);
            if (j >= 0)
            {
                result = html.substring(0, j);
            } else
            {
                return "";
            }
        }
        return result.replaceAll("\\<.*?\\>", "").replaceAll("\\s+", " ").trim();
    }

    public static String extractData(String html, String s2, String s3)
    {
        int i = html.indexOf(s2);
        String result = "";
        if (i >= 0)
        {
            int j = (html = html.substring(i + s2.length())).indexOf(s3);
            if (j >= 0)
            {
                result = html.substring(0, j);
            } else
            {
                return "";
            }
        }
        return HTMLUtil.convertCharacterEntities((String) result.replaceAll("&nbsp;", " ").replaceAll("\\<.*?\\>", "").replaceAll("\\s+", " ").trim());
    }

    public static String extractDataWithHTML(String html, String s2, String s3)
    {
        int i = html.indexOf(s2);
        String result = "";
        if (i >= 0)
        {
            int j = (html = html.substring(i + s2.length())).indexOf(s3);
            if (j >= 0)
            {
                result = html.substring(0, j);
            } else
            {
                return "";
            }
        }
        return HTMLUtil.convertCharacterEntities((String) result.replaceAll("&nbsp;", " ").replaceAll("\\s+", " ").trim());
    }

    public static String getHTML(String address)
    {
        String html = "";
        String ip = "";
        for (int j = 0; j < 20; ++j)
        {
            try
            {
                int read;
                html = "";
                URLConnection conn = null;
                URL url = new URL(address);
                conn = url.openConnection();
                conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 5.1; rv:14.0) Gecko/20100101 Firefox/14.0.1");
                conn.setReadTimeout(10000);
                conn.setConnectTimeout(10000);
                InputStream is = conn.getInputStream();
                InputStreamReader reader = new InputStreamReader(is, "UTF-8");
                char[] buf = new char[16384];
                StringBuffer sb = new StringBuffer();
                while ((read = reader.read(buf)) > 0)
                {
                    sb.append(buf, 0, read);
                }
                html = sb.toString();
                if (html.indexOf("Internal Server Error - Read") < 0)
                {
                    break;
                }
                Thread.sleep(3000);
                throw new UnknownHostException();
            }
            catch (Exception ex)
            {
                if (ex.getClass().toString().indexOf("ConnectException") >= 0 || ex.getClass().toString().indexOf("Socket") >= 0 || ex.getClass().toString().indexOf("Time") >= 0 || ex.getMessage().toString().indexOf(" 403 ") >= 0)
                {
                    --j;
                    try
                    {
                        Thread.sleep(1000);
                        continue;
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    continue;
                }
                if (ex.getClass().toString().indexOf("IOException") >= 0)
                {
                    try
                    {
                        Thread.sleep(1000);
                        continue;
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    continue;
                }
                if (ex.getClass().toString().indexOf("UnknownHostException") >= 0)
                {
                    try
                    {
                        --j;
                        Thread.sleep(1000);
                        continue;
                    }
                    catch (InterruptedException e)
                    {
                        e.printStackTrace();
                    }
                    continue;
                }
                if (ex.getClass().toString().indexOf("FileNotFoundException") >= 0)
                {
                    j = 110;
                    continue;
                }
                try
                {
                    Thread.sleep(1000);
                    continue;
                }
                catch (InterruptedException e)
                {
                    e.printStackTrace();
                }
            }
        }
        return html;
    }
}
