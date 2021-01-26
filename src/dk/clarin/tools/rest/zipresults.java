/*
    Copyright 2014, Bart Jongejan
    This file is part of the DK-ClarinTools.

    DK-ClarinTools is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    DK-ClarinTools is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with DK-ClarinTools.  If not, see <http://www.gnu.org/licenses/>.
*/
package dk.clarin.tools.rest;

import dk.cst.bracmat;
import dk.clarin.tools.ToolsProperties;
import dk.clarin.tools.workflow;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.zip.*;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
  * zipresults
  *
  * Show links to all results, intermediary or not.
  *
  */

@SuppressWarnings("serial")
public class zipresults extends HttpServlet 
    {
    private static final Logger logger = LoggerFactory.getLogger(workflow.class);

    private String date;
    private bracmat BracMat;

    public void init(ServletConfig config) throws ServletException 
        {
        InputStream fis = config.getServletContext().getResourceAsStream("/WEB-INF/classes/properties.xml");
        ToolsProperties.readProperties(fis);        
        Calendar cal = Calendar.getInstance();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        date = sdf.format(cal.getTime());
        BracMat = new bracmat(ToolsProperties.bootBracmat);
        super.init(config);
        }

    public void doGetZip(String localFilePath, String fileName,HttpServletResponse response)
        throws ServletException, IOException 
        {
        response.setStatus(200);

        response.setContentType("application/zip");
        response.setHeader("Content-Disposition","attachment;filename=\"" + fileName + "\"");
        try
            {
            int nLen = 0;
            OutputStream out;
            InputStream in = Files.newInputStream(Paths.get(localFilePath+fileName));

            out = response.getOutputStream();
            byte[] bBuf = new byte[1024];
            try
                {
                while ((nLen = in.read(bBuf, 0, 1024)) != -1)
                    {
                    out.write(bBuf, 0, nLen);
                    }
                }
            finally
                {
                in.close();
                }
            }
        catch (FileNotFoundException e)
            {
            response.setContentType("text/html; charset=UTF-8");
            response.setStatus(404);
            PrintWriter out = response.getWriter();
            if(fileName.charAt(0) == '/')
                fileName = fileName.substring(1);
            out.println("File " + fileName + " is no longer accessible.");
            }
        }

    public void doPost(HttpServletRequest request,HttpServletResponse response)
        throws ServletException, IOException 
        {
        response.setContentType("text/html; charset=UTF-8");
        response.setStatus(200);
        if(BracMat.loaded())
            {
            @SuppressWarnings("unchecked")
            Enumeration<String> parmNames = (Enumeration<String>)request.getParameterNames();

            String shortletter="";
            String job = "";
            for (Enumeration<String> e = parmNames ; e.hasMoreElements() ;) 
                {
                String parmName = e.nextElement();
                if(parmName.equals("shortletter"))
                    shortletter = request.getParameterValues(parmName)[0];
                else if(parmName.equals("JobNr"))
                    job = request.getParameterValues(parmName)[0];
                }
            if(!job.equals(""))
                {
                String letter = BracMat.Eval("letter$(" + job + "."+ shortletter +")");
                String readme = BracMat.Eval("readme$(" 
                                            + job 
                                            + "." 
                                            + workflow.quote(date) 
                                            + "." 
                                            + workflow.quote(letter) 
                                            + ")"
                                            );
                String localFilePath = ToolsProperties.documentRoot;
                    
                OutputStream zipdest = null;
                ZipOutputStream zipout = null;
                boolean hasFiles = false;
                String jobzip = job + (shortletter.startsWith("y") ? "-final" :"-all") +".zip";
                if(letter.startsWith("file:"))
                    {
                    hasFiles = true;
                    zipdest = Files.newOutputStream(Paths.get(localFilePath + jobzip));

                    zipout = new ZipOutputStream(new BufferedOutputStream(zipdest));
                    while(letter.startsWith("file:"))
                        {
                        int end = letter.indexOf(';');
                        String filename = letter.substring(5,end);
                        String zipname = filename;
                        int zipnameStart = filename.indexOf('*');
                        if(zipnameStart > 0)
                            {
                            zipname = filename.substring(zipnameStart+1);
                            filename = filename.substring(0,zipnameStart);
                            }
                        logger.debug("workflowzip("+localFilePath + filename+","+zipname+")");        
                        workflow.zip(localFilePath + filename,zipname,zipout);
                        letter = letter.substring(end+1);
                        }
                    }

                if(hasFiles)
                    {
                    workflow.zipstring("readme.txt",zipout,readme);
                    workflow.zipstring("index.html",zipout,letter);
                    zipout.close();
                    }
                doGetZip(localFilePath, jobzip,response);                            
                }
            }
        else
            {
            throw new ServletException("Bracmat is not loaded. Reason:" + BracMat.reason());
            }
        }

        public void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException
            {        
            doPost(request, response);
            }
    }

