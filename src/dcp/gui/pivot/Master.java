package dcp.gui.pivot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.util.Locale;

import org.apache.pivot.beans.BXML;
import org.apache.pivot.beans.BXMLSerializer;
import org.apache.pivot.beans.Bindable;
import org.apache.pivot.collections.ArrayList;
import org.apache.pivot.collections.List;
import org.apache.pivot.collections.Map;
import org.apache.pivot.collections.Sequence;
import org.apache.pivot.util.Filter;
import org.apache.pivot.util.Resources;
import org.apache.pivot.wtk.Action;
import org.apache.pivot.wtk.Application;
import org.apache.pivot.wtk.Button;
import org.apache.pivot.wtk.ButtonPressListener;
import org.apache.pivot.wtk.Component;
import org.apache.pivot.wtk.ComponentKeyListener;
import org.apache.pivot.wtk.DesktopApplicationContext;
import org.apache.pivot.wtk.Display;
import org.apache.pivot.wtk.FileBrowserSheet;
import org.apache.pivot.wtk.FileBrowserSheetListener;
import org.apache.pivot.wtk.Keyboard;
import org.apache.pivot.wtk.Keyboard.Modifier;
import org.apache.pivot.wtk.Label;
import org.apache.pivot.wtk.MessageType;
import org.apache.pivot.wtk.Prompt;
import org.apache.pivot.wtk.PushButton;
import org.apache.pivot.wtk.TabPane;
import org.apache.pivot.wtk.TabPaneSelectionListener;
import org.apache.pivot.wtk.Window;
import org.apache.pivot.wtk.Keyboard.KeyLocation;

import dcp.config.io.IOFactory;
import dcp.logic.factory.TypeFactory.FILE_TYPE;
import dcp.gui.pivot.actions.BrowseAction;
import dcp.gui.pivot.frames.BuildFrame;
import dcp.gui.pivot.frames.ScanFrame;
import dcp.gui.pivot.frames.SetFrame;
import dcp.gui.pivot.frames.TweakFrame;
import dcp.gui.pivot.helper.HelperFacade;
import dcp.logic.factory.CastFactory;
import dcp.logic.factory.GroupFactory;
import dcp.logic.factory.PackFactory;
import dcp.logic.model.Group;
import dcp.logic.model.Pack;
import dcp.logic.model.Pack_1_0;
import dcp.logic.factory.TypeFactory.INSTALL_TYPE;
import dcp.logic.model.config.AppConfig;
import dcp.logic.model.config.SetupConfig;
import dcp.main.log.Out;


public class Master extends Window implements Application, Bindable
{

    private Window window;//Main application window
    
    //Data loaded from saved file
    List<Pack> packs;
    List<Group> groups;
    //Tabs
    private ScanFrame scanFrame;
    private SetFrame setFrame;
    private TweakFrame tweakFrame;
    private BuildFrame buildFrame;
    //Helpers
    @BXML static public HelperFacade helper;
    //Display
    @BXML private Label info;
    @BXML private TabPane tabPane;
    @BXML private Label statusBarStep;
    @BXML private Label statusBarNPacks;
    @BXML private Label statusBarNGroups;
    //Browser
    @BXML private FileBrowserSheet saveFileBrowserSheet;//File Browser
    @BXML private FileBrowserSheet loadFileBrowserSheet;//File Browser
    //Buttons
    @BXML private PushButton btBack;
    @BXML private PushButton btNext;
    @BXML private PushButton btSaveAs;
    @BXML private PushButton btSave;
    @BXML private PushButton btLoad;
    @BXML private PushButton btUndo;
    @BXML private PushButton btDefault;
    @BXML private PushButton btHelp;
    //@BXML private PushButton btInfo;
    
    //Actions
    private Action ASave;//Save project on file
    private Action ALoad;//Load project from file
    
    //Constant Application Values
    public final static String AppName = "DCP Setup Maker";
    public final static String AppVersion = "1.1.0";
    public static AppConfig appConfig;//App configuration file
    public static SetupConfig setupConfig;//Setup configuration file
    
    /**
     * Update title with save file and edit flag '*'
     */
    private void titleUpdate() {
        if (!Master.this.getTitle().contains("-")) {//No save file defined
            if (Master.this.getTitle().contains("*"))//remove * flag from title
                Master.this.setTitle(Master.this.getTitle().substring(0, Master.this.getTitle().length()-1));
            Master.this.setTitle(Master.this.getTitle().concat(" - "+IOFactory.saveFile ));
        }
        else {//Already saved on a file
            Master.this.setTitle(Master.this.getTitle().
                substring(0, Master.this.getTitle().indexOf('-')+2).
                concat(IOFactory.saveFile ));
        }
    }
    
    public Master() {
        //Save action
        ASave = new Action() {//Project Save Action
            @Override public void perform(Component source) {
                if (save(IOFactory.saveFile)) {
                    //Enable Save button
                    if (!btSave.isEnabled()) {
                        btSave.setEnabled(true);
                        btUndo.setEnabled(true);
                        //btDefault.setEnabled(false);
                    }
                    titleUpdate();
                }
            } };
        //Load Action
        ALoad = new Action() {//Project Load Action
            @Override
            public void perform(Component arg0)
            {
                if (load(IOFactory.saveFile)) {
                    //Clear scanned path
                    scanFrame.init(setupConfig.getSrcPath());
                    //Groups binding
                    ScanFrame.setGroups(groups);//loaded flag enabled*
                    //Packs binding
                    scanFrame.setPacks(packs);//loaded flag enabled*
                    //Tab data initialize
                    setFrame.loadInit();
                    //SetupConfig binding
                    tweakFrame.dataBinding(setupConfig);
                    //Go to Set tab
                    tabPane.setSelectedIndex(1);
                    for(int i=0; i<tabPane.getTabs().getLength(); i++)
                        tabPane.getTabs().get(i).setEnabled(false);
                    tabPane.getTabs().get(1).setEnabled(true);
                    if (tabPane.getSelectedIndex() == 1)//If already on Set tab
                        ScanFrame.setLoaded(false);//Disable scan loaded flag*
                    btBack.setEnabled(true);
                    btNext.setEnabled(true);
                    //Enable Save button
                    if (!btSave.isEnabled()) {
                        btSave.setEnabled(true);
                        btUndo.setEnabled(true);
                    }
                    titleUpdate();
                }
            }
        };
    }

    @Override public void startup(Display display, Map<String, String> properties) throws Exception//App start
    {
        Out.print("PIVOT", "Window open");
        IOFactory.init();//Factory memory data initialize
        appConfig = confLoad();//Load configuration file
        if (appConfig == null)
        {
            appConfig = new AppConfig(AppName, AppVersion);//Init config if not exists
            setupConfig = new SetupConfig("Package", "1.0.0");
        }
        else
        {
            appConfig.setAppName(AppName); appConfig.setAppVersion(AppVersion);
            setupConfig = new SetupConfig(appConfig.getDefaultSetupConfig());
        }
        Out.print("FACTORY", "Data loaded to memory");
        
        Locale.setDefault(Locale.ENGLISH);//Set default UI language to English
        BXMLSerializer bxmlSerializer = new BXMLSerializer();
        window = (Window) bxmlSerializer.readObject(getClass().getResource("master.bxml"));
        window.open(display);
        
        //Helper launch if first time
        if (appConfig.isHelp()) {
            helper.open(window);
            appConfig.setHelp(false);
        }
    }

    @Override public boolean shutdown(boolean optional) throws Exception//Window close
    {
        if (window != null) {
            window.close();
            if (appConfig.isModified()) confSave(appConfig);//Save configuration if modified
            Out.print("PIVOT", "Window closed.");
        }
        return false;
    }

    @Override public void resume() throws Exception { }
    @Override public void suspend() throws Exception { }

    @Override public void initialize(Map<String, Object> arg0, URL arg1, Resources arg2)
    {
        //Shortcuts define
        this.getComponentKeyListeners().add(new ComponentKeyListener.Adapter() {
            @Override
            public boolean keyPressed(Component component, int keyCode, KeyLocation keyLocation)
            {
                switch(keyCode) {
                case Keyboard.KeyCode.F1://[F1] - Help
                    if (btHelp.isEnabled()) btHelp.press();
                    return true;
                default:
                    break;
                }
                if (Keyboard.isPressed(Modifier.CTRL)) {//CTRL + [keycode]
                    switch(keyCode) {
                    case Keyboard.KeyCode.O://[ctrl+O] - load project
                        if (btLoad.isEnabled()) btLoad.press();
                        return true;
                    case Keyboard.KeyCode.S://[ctrl+S] - save project
                        if (Keyboard.isPressed(Modifier.SHIFT) || !btSave.isEnabled())
                            btSaveAs.press();//Save As
                        else btSave.press();//Save
                        return true;
                    case Keyboard.KeyCode.Z://[ctrl+Z] - undo all
                        if (btUndo.isEnabled()) btUndo.press();
                        return true;
                    case Keyboard.KeyCode.LEFT://[ctrl+LEFT] - previous step
                        if (btBack.isEnabled()) btBack.press();
                        return true;
                    case Keyboard.KeyCode.RIGHT://[ctrl+RIGHT] - next step
                        if (btNext.isEnabled()) btNext.press();
                        return true;
                    default:
                        break;
                    }   
                }
                else if (Keyboard.isPressed(Modifier.ALT)) {//ALT + [keycode]
                    switch(keyCode) {
                    case Keyboard.KeyCode.F4://[alt+F4] - quit
                        break;
                    default:
                        break;
                    }
                }
                return false;
            }
        });
        
        //Tabs singletons init
        scanFrame = ScanFrame.getSingleton();
        setFrame = SetFrame.getSingleton();
        tweakFrame = TweakFrame.getSingleton();
        buildFrame = BuildFrame.getSingleton();
        
        //Version display
        //info.setText(AppName + " " + AppVersion + " ");
        info.setText(AppVersion + " ");
        
        //Actions binding
        btSaveAs.setAction(new BrowseAction(saveFileBrowserSheet));
        btLoad.setAction(new BrowseAction(loadFileBrowserSheet));
        
        //Set default directory selection in File Browser (packs/)
        try {//Set working directory as root directory for file browser
            File saveDir = new File(IOFactory.savePath); 
            if (!saveDir.exists())
                saveDir = new File(".");
            saveFileBrowserSheet.setRootDirectory(saveDir.getCanonicalFile());
            loadFileBrowserSheet.setRootDirectory(saveDir.getCanonicalFile());
        } catch (IOException e) {
            e.printStackTrace();
        }
        saveFileBrowserSheet.setMode(FileBrowserSheet.Mode.SAVE_AS);//FileBrowser Mode to File save
        loadFileBrowserSheet.setMode(FileBrowserSheet.Mode.OPEN);//FileBrowser Mode to File select
        
        loadFileBrowserSheet.setDisabledFileFilter(new Filter<File>() {//Filter for dcp files only
            @Override
            public boolean include(File file)
            {
                if (file.isDirectory() || file.getName().endsWith("."+IOFactory.dcpFileExt))
                    return false;
                return true;
            }
        });
        
        //File select in File Browser Event
        saveFileBrowserSheet.getFileBrowserSheetListeners().add(new FileBrowserSheetListener.Adapter() {
            @Override
            public void selectedFilesChanged(FileBrowserSheet fileBrowserSheet, Sequence<File> previousSelectedFiles)
            {
                //If save file changed
                if (fileBrowserSheet.getSelectedFile() != null) {
                    try {
                        String s = fileBrowserSheet.getSelectedFile().getCanonicalPath();
                        if (!s.equals(IOFactory.saveFile)) {
                            IOFactory.setSaveFile(s);
                            ASave.perform(fileBrowserSheet);
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        loadFileBrowserSheet.getFileBrowserSheetListeners().add(new FileBrowserSheetListener.Adapter() {
            @Override
            public void selectedFilesChanged(FileBrowserSheet fileBrowserSheet, Sequence<File> previousSelectedFiles)
            {
                //If save file changed
                if (fileBrowserSheet.getSelectedFile() != null) {
                    try {
                        String s = fileBrowserSheet.getSelectedFile().getCanonicalPath();
                        if (!s.equals(IOFactory.saveFile)) {
                            IOFactory.setSaveFile(s);
                            ALoad.perform(fileBrowserSheet);
                            Prompt.prompt(MessageType.INFO, "Data loaded from saved project.", getWindow());
                        }
                    }
                    catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        });
        
        //Buttons listeners
        btBack.getButtonPressListeners().add(new ButtonPressListener() {
            @Override public void buttonPressed(Button bt)
            {
                if (!btNext.isEnabled()) btNext.setEnabled(true);
                
                if (tabPane.getSelectedIndex()>0) {
                    tabPane.getTabs().get(tabPane.getSelectedIndex()).setEnabled(false);
                    tabPane.setSelectedIndex(tabPane.getSelectedIndex()-1);
                    tabPane.getTabs().get(tabPane.getSelectedIndex()).setEnabled(true);
                }
                
                if (tabPane.getSelectedIndex() == 0)
                    bt.setEnabled(false);
            }
        });
        btNext.getButtonPressListeners().add(new ButtonPressListener() {
            @Override public void buttonPressed(Button bt)
            {
                if (!btBack.isEnabled()) btBack.setEnabled(true);
                
                if (tabPane.getSelectedIndex()<tabPane.getTabs().getLength()-1) {
                    tabPane.getTabs().get(tabPane.getSelectedIndex()).setEnabled(false);
                    tabPane.setSelectedIndex(tabPane.getSelectedIndex()+1);
                    tabPane.getTabs().get(tabPane.getSelectedIndex()).setEnabled(true);
                }
                
                if (tabPane.getSelectedIndex() == tabPane.getTabs().getLength()-1)
                    bt.setEnabled(false);
            }
        });
        btSave.getButtonPressListeners().add(new ButtonPressListener() {
            @Override public void buttonPressed(Button bt)
            {
                ASave.perform(bt);
                Prompt.prompt(MessageType.INFO, "Project data saved.", getWindow());
            }
        });
        btUndo.getButtonPressListeners().add(new ButtonPressListener() {
            @Override public void buttonPressed(Button bt)
            {
                ALoad.perform(bt);
            }
        });
        btDefault.getButtonPressListeners().add(new ButtonPressListener() {
            @Override public void buttonPressed(Button bt)
            {
                appConfig.setDefaultSetupConfig(new SetupConfig(setupConfig));
                appConfig.setScanMode(ScanFrame.getScanMode());
                Out.print("INFO", "Default User data saved.");
                Prompt.prompt(MessageType.INFO, "User data saved as default.", getWindow());
            }
        });
        btHelp.getButtonPressListeners().add(new ButtonPressListener() {
            @Override public void buttonPressed(Button bt)
            {
                helper.setIndex(tabPane.getSelectedIndex());
                helper.open(Master.this, null);
            }
        });
        
        //Tabs selection listener
        tabPane.getTabPaneSelectionListeners().add(new TabPaneSelectionListener.Adapter() {
            @Override public void selectedIndexChanged(TabPane tabPane, int previousSelectedIndex)
            {
                switch(tabPane.getSelectedIndex()) {
                    case 0://Scan Tab
                        break;
                    case 1://Set Tab
                        if (scanFrame.isModified() && !ScanFrame.isLoaded()) {//If Scanned directory
                            scanFrame.setModified(false);
                            setFrame.scanInit();//Data export to Set tab
                            if (!Master.this.getTitle().contains("*"))
                                Master.this.setTitle(Master.this.getTitle().concat("*"));//modified flag in Title
                        }
                        else ScanFrame.setLoaded(false);//Disable scan loaded flag*
                        break;
                    case 2://Tweak Tab
                        if (setFrame.isModified()) {
                            setFrame.setModified(false);//Modified flag*
                            boolean found = false;
                            for(Pack p:PackFactory.getPacks())
                                if (p.isShortcut() && p.getInstallType() != INSTALL_TYPE.EXECUTE) {
                                    found = true;
                                    break;
                                }
                            tweakFrame.setPackShortcuts(found);
                            tweakFrame.setModified(true);
                            if (!Master.this.getTitle().contains("*"))
                                Master.this.setTitle(Master.this.getTitle().concat("*"));//modified flag in Title
                        }
                        break;
                    case 3://Build Tab
                        if (tweakFrame.isModified()) {
                            tweakFrame.setModified(false);//Modified flag*
                            buildFrame.init(setupConfig.getAppName(), setupConfig.getAppVersion());
                            if (!Master.this.getTitle().contains("*"))
                                Master.this.setTitle(Master.this.getTitle().concat("*"));//modified flag in Title
                        }
                        break;
                    default:
                        break;
                }
                
                updateStatusBar();
            }
        });
        
        updateStatusBar();
    }
    
    /**
     * Updates status bar info
     * step nbr, nbr of packs, nbr of groups
     */
    public void updateStatusBar()
    {
        statusBarStep.setText(String.valueOf(tabPane.getSelectedIndex()+1) + "/" +
                                String.valueOf(tabPane.getTabs().getLength()) + ",");
        statusBarNPacks.setText(String.valueOf(PackFactory.getPacks().getLength()));
        statusBarNGroups.setText(String.valueOf(GroupFactory.getGroups().getLength()));
    }

    /**
     * Save current application configuration
     */
    private void confSave(AppConfig appConfig) {
        try
        {
            File f = new File(IOFactory.confFile);
            f.createNewFile();
            FileOutputStream out = new FileOutputStream(f);
            ObjectOutputStream os = new ObjectOutputStream(out);
            
            os.writeObject(appConfig);
            
            os.close();
            out.close();
            Out.print("INFO", appConfig.getAppName() + " " + appConfig.getAppVersion() +
                      " configuration saved to " + IOFactory.confFile);
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Load application configuration from file
     */
    private AppConfig confLoad() {
        try
        {
            File f = new File(IOFactory.confFile);
            if (f.exists()) {
                FileInputStream in = new FileInputStream(f);
                ObjectInputStream is = new ObjectInputStream(in);
                
                AppConfig appConfig = (AppConfig) is.readObject();
                
                is.close();
                in.close();
                
                return appConfig;
            }
        }
        catch (IOException e) {
            e.printStackTrace();
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
    
    /**
     * Save data to file
     * [SETUP_CONFIG][GROUPS][PACKS]
     */
    private boolean save(String saveFile) {
        try
        {
            File f = new File(saveFile);
            if (!f.exists()) f.createNewFile();//create file if not exists
            FileOutputStream out = new FileOutputStream(f);
            ObjectOutputStream os = new ObjectOutputStream(out);
            
            os.writeObject(Master.AppVersion);
            
            os.writeObject(setupConfig);
            
            os.writeInt(GroupFactory.getGroups().getLength());
            for(Group g:GroupFactory.getGroups())
                os.writeObject(g);
            
            os.writeInt(PackFactory.getPacks().getLength());
            for(Pack p:PackFactory.getPacks())
                os.writeObject(p);
            
            os.close();
            out.close();
            Out.print("INFO", setupConfig.getAppName() + " " + setupConfig.getAppVersion() +
                      " data saved to " + f.getAbsolutePath());
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    /**
     * Load data from file
     */
    private boolean load(String saveFile) {
        try
        {
            File f = new File(saveFile);
            if (!f.exists()) {//If save file not exists
                return false;//error
            }
            else {//File exists
                FileInputStream in = new FileInputStream(f);
                ObjectInputStream is = new ObjectInputStream(in);
                
                String version = (String) is.readObject();
                Out.print("INFO", "File version: "+version);
                
                setupConfig = (SetupConfig) is.readObject();
                Out.print("INFO", setupConfig.getAppName() + " " + setupConfig.getAppVersion());
                
                groups = new ArrayList<Group>();
                int nGroups = is.readInt();
                for(int i = 0; i<nGroups; i++) {
                    Group G = (Group) is.readObject();
                    groups.add(G);
                }
                if (nGroups > 0) Out.print("INFO", groups.getLength() + " groups loaded");
                
                packs = new ArrayList<Pack>();
                int nPacks = is.readInt();
                for(int i = 0; i<nPacks; i++) {
                    Pack P;
                    if (version.startsWith("1.0"))// cast Pack model from 1.0.x version
                        P = new Pack((Pack_1_0) is.readObject());
                    else
                        P = (Pack) is.readObject();
                    P.setIcon(CastFactory.nameToImage(P.getName(), P.getFileType() == FILE_TYPE.Folder));
                    packs.add(P);
                }
                if (nPacks > 0) Out.print("INFO", packs.getLength() + " packs loaded");
                
                is.close();
                in.close();
                Out.print("INFO", "Data loaded from file "+IOFactory.saveFile);
            }
        }
        catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        catch (ClassNotFoundException e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }
    
    /**
     * Launch the Pivot application
     * @param args
     */
    public static void main(String[] args)
    {
        DesktopApplicationContext.main(Master.class, args);
    }

}
