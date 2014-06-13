
[y yprime] = compareToDeriv('track2Sub1Packet.wav');

isSignalHigh = false;
foundFirstZeroCrossing = false;
samplesSinceLastXing = 0;

minimumZeroXingSamples = todo;


if foundFirstZeroCrossing == true
    if(samplesSinceLastXing >= minimumZeroXingSamples ) 

        if((isSignalHigh && isBelowZero(sample)) || (!isSignalHigh && isAboveZero(sample)))
        
            handleZeroCrossing();
            samplesSinceLastXing = 0;
            isSignalHigh = !isSignalHigh;
         
        end
    end
else 
    if(isZeroCrossing(previousSample, sample)) {
        foundFirstZeroCrossing = true;
        samplesSinceLastXing = 0;
        isSignalHigh = isAboveZero(sample);
    end

end