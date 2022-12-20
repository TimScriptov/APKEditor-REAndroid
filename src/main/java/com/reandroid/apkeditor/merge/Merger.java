package com.reandroid.apkeditor.merge;

import com.reandroid.apkeditor.BaseCommand;
import com.reandroid.apkeditor.Util;
import com.reandroid.apkeditor.common.AndroidManifestHelper;
import com.reandroid.archive.APKArchive;
import com.reandroid.archive.WriteProgress;
import com.reandroid.archive.ZipAlign;
import com.reandroid.commons.command.ARGException;
import com.reandroid.commons.utils.log.Logger;
import com.reandroid.lib.apk.APKLogger;
import com.reandroid.lib.apk.ApkBundle;
import com.reandroid.lib.apk.ApkModule;
import com.reandroid.lib.arsc.chunk.xml.AndroidManifestBlock;

import java.io.File;
import java.io.IOException;
import java.util.zip.ZipEntry;

public class Merger extends BaseCommand implements WriteProgress {
    private final MergerOptions options;
    private APKLogger mApkLogger;
    public Merger(MergerOptions options){
        this.options=options;
    }
    public void run() throws IOException {
        log("Searching apk files ...");
        ApkBundle bundle=new ApkBundle();
        bundle.setAPKLogger(getAPKLogger());
        bundle.loadApkDirectory(options.inputFile);
        log("Found modules: "+bundle.getApkModuleList().size());
        ApkModule mergedModule=bundle.mergeModules();
        if(options.resDirName!=null){
            log("Renaming resources root dir: "+options.resDirName);
            mergedModule.setResourcesRootDir(options.resDirName);
        }
        if(options.validateResDir){
            log("Validating resources dir ...");
            mergedModule.validateResourcesDir();
        }
        removeSignature(mergedModule);
        if(mergedModule.hasAndroidManifestBlock()){
            sanitizeManifest(mergedModule.getAndroidManifestBlock());
        }
        log("Writing apk ...");
        mergedModule.writeApk(options.outputFile, this);
        log("Zip align ...");
        ZipAlign.align4(options.outputFile);
        log("Saved to: "+options.outputFile);
        log("Done");
    }
    private void sanitizeManifest(AndroidManifestBlock manifest){
        log("Sanitizing manifest ...");
        boolean removed = AndroidManifestHelper.removeApplicationAttribute(manifest,
                AndroidManifestBlock.ID_extractNativeLibs);
        if(removed){
            log("Removed: "+AndroidManifestBlock.NAME_extractNativeLibs);
        }
        removed = AndroidManifestHelper.removeApplicationAttribute(manifest,
                AndroidManifestBlock.ID_isSplitRequired);
        if(removed){
            log("Removed: "+AndroidManifestBlock.NAME_isSplitRequired);
        }
    }
    private void removeSignature(ApkModule module){
        module.removeDir("META-INF");
        APKArchive archive = module.getApkArchive();
        archive.remove("stamp-cert-sha256");
    }
    @Override
    public void onCompressFile(String path, int method, long length) {
        StringBuilder builder=new StringBuilder();
        builder.append("Writing:");
        if(method == ZipEntry.STORED){
            builder.append(" method=STORED");
        }
        builder.append(" total=");
        builder.append(length);
        builder.append(" bytes : ");
        if(path.length()>30){
            path=path.substring(path.length()-30);
        }
        builder.append(path);
        logSameLine(builder.toString());
    }
    private APKLogger getAPKLogger(){
        if(mApkLogger!=null){
            return mApkLogger;
        }
        mApkLogger = new APKLogger() {
            @Override
            public void logMessage(String msg) {
                Logger.i(getLogTag()+msg);
            }
            @Override
            public void logError(String msg, Throwable tr) {
                Logger.e(getLogTag()+msg, tr);
            }
            @Override
            public void logVerbose(String msg) {
                if(msg.length()>30){
                    msg=msg.substring(msg.length()-30);
                }
                Logger.sameLine(getLogTag()+msg);
            }
        };
        return mApkLogger;
    }
    public static void execute(String[] args) throws ARGException, IOException {
        if(Util.isHelp(args)){
            throw new ARGException(MergerOptions.getHelp());
        }
        MergerOptions option=new MergerOptions();
        option.parse(args);
        File outDir=option.outputFile;
        Util.deleteEmptyDirectories(outDir);
        if(outDir.exists()){
            if(!option.force){
                throw new ARGException("Path already exists: "+outDir);
            }
            log("Deleting: "+outDir);
            Util.deleteDir(outDir);
        }
        log("Merging ...\n"+option);
        Merger merger=new Merger(option);
        merger.run();
    }
    private static void logSameLine(String msg){
        Logger.sameLine(getLogTag()+msg);
    }
    private static void log(String msg){
        Logger.i(getLogTag()+msg);
    }
    private static String getLogTag(){
        return "[MERGE] ";
    }
    public static boolean isCommand(String command){
        if(Util.isEmpty(command)){
            return false;
        }
        command=command.toLowerCase().trim();
        return command.equals(ARG_SHORT) || command.equals(ARG_LONG);
    }
    public static final String ARG_SHORT="m";
    public static final String ARG_LONG="merge";
    public static final String DESCRIPTION="Merges split apk files from directory";
}