package com.ws.videoview.videoview.opengl;

public class CropRect{
    public CropRect(){
        this(0, 0, 0, 0);
    }
    public CropRect(int x, int y, int width, int height){
        xOffset = x;
        yOffset = y;
        cropWidth = width;
        cropHeight = height;
    }
    public int xOffset;
    public int yOffset;
    public int cropWidth;
    public int cropHeight;
}